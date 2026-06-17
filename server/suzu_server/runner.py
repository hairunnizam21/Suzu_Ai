"""Background agent runner with pub/sub event bus.

The previous design tied an agent run to a single HTTP request: when the phone
disconnected (closed app, switched chat, lost network) the run died with it.

This module decouples runs from requests:

- A `Run` is a long-lived task scheduled on the FastAPI event loop. Started by
  POST /v1/chat, it survives across reconnects.
- Each Run owns an event log (`history`) plus a fan-out queue per subscriber.
  New subscribers are replayed the full history then receive live events.
- Runs accept *injections* — extra user messages pushed in mid-flight that the
  agent will pick up on the next iteration. This is how the user adds
  corrections / extra instructions without having to wait for the agent to
  finish.
- Cancellation is cooperative: `Run.cancel()` flips a flag the agent loop
  checks between iterations.

The runner intentionally lives in process memory only — restarting the server
drops in-flight runs. Persisted history (DB) is still updated by the agent
loop so a chat can be resumed by issuing a fresh prompt.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from dataclasses import dataclass, field
from typing import Any

from .schemas import ChatRequest, Message

logger = logging.getLogger("suzu.runner")


@dataclass
class _Subscriber:
    queue: asyncio.Queue[dict[str, Any]]
    cursor: int = 0  # index into Run.history already delivered


@dataclass
class Run:
    chat_id: str
    request: ChatRequest
    started_at: float = field(default_factory=time.time)
    history: list[dict[str, Any]] = field(default_factory=list)
    subscribers: list[_Subscriber] = field(default_factory=list)
    pending_injections: list[Message] = field(default_factory=list)
    done: bool = False
    cancelled: bool = False
    task: asyncio.Task | None = None
    last_event_at: float = field(default_factory=time.time)

    def publish(self, event: dict[str, Any]) -> None:
        """Append an event to history and fan it out to all subscribers."""

        self.history.append(event)
        self.last_event_at = time.time()
        for sub in list(self.subscribers):
            try:
                sub.queue.put_nowait(event)
            except asyncio.QueueFull:
                # If a slow subscriber is stuck, drop it — they'll reconnect
                # and replay from cursor.
                logger.warning("subscriber queue full for chat=%s, dropping", self.chat_id)
                self._drop(sub)

    def subscribe(self) -> _Subscriber:
        sub = _Subscriber(queue=asyncio.Queue(maxsize=2048), cursor=0)
        # Replay full history first so reconnects see the same stream
        for ev in self.history:
            try:
                sub.queue.put_nowait(ev)
            except asyncio.QueueFull:
                # Pathological — history bigger than queue. Trim to fit.
                pass
        sub.cursor = len(self.history)
        self.subscribers.append(sub)
        return sub

    def _drop(self, sub: _Subscriber) -> None:
        try:
            self.subscribers.remove(sub)
        except ValueError:
            pass

    def unsubscribe(self, sub: _Subscriber) -> None:
        self._drop(sub)

    def inject(self, message: Message) -> None:
        """Queue an extra user message to be appended before the next turn."""

        self.pending_injections.append(message)
        self.publish({
            "type": "inject_ack",
            "content": message.content,
            "queued_at": int(time.time()),
        })

    def cancel(self) -> None:
        self.cancelled = True
        if self.task is not None and not self.task.done():
            self.task.cancel()


class RunRegistry:
    """In-memory registry of live runs, keyed by chat_id."""

    def __init__(self) -> None:
        self._runs: dict[str, Run] = {}
        self._lock = asyncio.Lock()

    async def get(self, chat_id: str) -> Run | None:
        return self._runs.get(chat_id)

    async def put(self, run: Run) -> None:
        async with self._lock:
            existing = self._runs.get(run.chat_id)
            if existing and not existing.done:
                existing.cancel()
            self._runs[run.chat_id] = run

    async def discard(self, chat_id: str) -> None:
        async with self._lock:
            self._runs.pop(chat_id, None)

    async def gc(self, max_age_seconds: float = 3600.0) -> None:
        now = time.time()
        async with self._lock:
            stale = [
                cid for cid, r in self._runs.items()
                if r.done and (now - r.last_event_at) > max_age_seconds
            ]
            for cid in stale:
                self._runs.pop(cid, None)

    def status_snapshot(self) -> list[dict[str, Any]]:
        return [
            {
                "chat_id": r.chat_id,
                "done": r.done,
                "cancelled": r.cancelled,
                "started_at": int(r.started_at),
                "last_event_at": int(r.last_event_at),
                "history_size": len(r.history),
                "subscribers": len(r.subscribers),
            }
            for r in self._runs.values()
        ]


registry = RunRegistry()


def new_chat_id() -> str:
    return uuid.uuid4().hex


__all__ = ["Run", "RunRegistry", "registry", "new_chat_id"]
