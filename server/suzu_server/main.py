"""FastAPI entrypoint."""

from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from . import __version__
from .agent import run_agent
from .auth import require_token
from .config import settings
from .db import (
    append_message,
    create_chat,
    delete_chat,
    get_chat,
    init_db,
    list_chats,
    rename_chat,
)
from .runner import Run, new_chat_id, registry
from .schemas import (
    ChatDetail,
    ChatRequest,
    ChatSummary,
    ConfigResponse,
    InjectRequest,
    Message,
    RenameRequest,
    RunStatus,
)
from .tools import tool_names

logger = logging.getLogger("suzu.main")


@asynccontextmanager
async def lifespan(app: FastAPI):  # noqa: D401
    await init_db()
    settings.workspace_root.mkdir(parents=True, exist_ok=True)
    logger.info("Suzu server %s starting on %s:%s", __version__, settings.host, settings.port)
    # Periodic GC of finished runs
    gc_task = asyncio.create_task(_gc_loop())
    try:
        yield
    finally:
        gc_task.cancel()


async def _gc_loop() -> None:
    while True:
        try:
            await asyncio.sleep(300)
            await registry.gc(max_age_seconds=3600)
        except asyncio.CancelledError:
            return
        except Exception:  # noqa: BLE001
            logger.exception("runner gc failed")


app = FastAPI(
    title="Suzu_Ai server",
    version=__version__,
    description="Devin-style coding agent over HTTP, paired with the Suzu_Ai Android app.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "version": __version__}


@app.get("/v1/config", response_model=ConfigResponse)
async def get_config(_: None = Depends(require_token)) -> ConfigResponse:
    return ConfigResponse(
        server_version=__version__,
        default_model=settings.default_model,
        default_max_iterations=settings.default_max_iterations,
        available_tools=tool_names(),
    )


@app.get("/v1/chats", response_model=list[ChatSummary])
async def chats_list(_: None = Depends(require_token)) -> list[ChatSummary]:
    rows = await list_chats()
    return [ChatSummary(**r) for r in rows]


@app.get("/v1/chats/{chat_id}", response_model=ChatDetail)
async def chat_detail(chat_id: str, _: None = Depends(require_token)) -> ChatDetail:
    row = await get_chat(chat_id)
    if not row:
        raise HTTPException(status_code=404, detail="chat not found")
    return ChatDetail(**row)


@app.patch("/v1/chats/{chat_id}")
async def chat_rename(
    chat_id: str,
    body: RenameRequest,
    _: None = Depends(require_token),
) -> dict[str, str]:
    await rename_chat(chat_id, body.title)
    return {"status": "ok"}


@app.delete("/v1/chats/{chat_id}")
async def chat_delete(chat_id: str, _: None = Depends(require_token)) -> dict[str, str]:
    run = await registry.get(chat_id)
    if run is not None:
        run.cancel()
    await registry.discard(chat_id)
    await delete_chat(chat_id)
    return {"status": "ok"}


@app.get("/v1/runs", response_model=list[RunStatus])
async def runs_list(_: None = Depends(require_token)) -> list[RunStatus]:
    return [RunStatus(**r) for r in registry.status_snapshot()]


@app.post("/v1/chats/{chat_id}/cancel")
async def chat_cancel(chat_id: str, _: None = Depends(require_token)) -> dict[str, str]:
    run = await registry.get(chat_id)
    if run is None:
        raise HTTPException(status_code=404, detail="no active run for this chat")
    run.cancel()
    return {"status": "cancelling"}


@app.post("/v1/chats/{chat_id}/inject")
async def chat_inject(
    chat_id: str,
    body: InjectRequest,
    _: None = Depends(require_token),
) -> dict[str, str]:
    """Push a follow-up user message into a live run between iterations."""

    run = await registry.get(chat_id)
    if run is None or run.done:
        raise HTTPException(status_code=409, detail="no live run; send via /v1/chat instead")
    msg = Message(role="user", content=body.content)
    run.inject(msg)
    # Persist immediately so chat history reflects the injection even before
    # the agent picks it up.
    await append_message(chat_id, msg.model_dump())
    return {"status": "queued"}


@app.post("/v1/chat")
async def chat_stream(
    req: ChatRequest,
    _: None = Depends(require_token),
) -> StreamingResponse:
    """Start (or attach to) an agent run and stream SSE events.

    The run survives client disconnects: closing the app or switching chats
    leaves the agent working in the background. Reconnect by calling
    `/v1/chats/{id}/stream` (replays history + live tail) or by re-POSTing
    here without `chat_id`.
    """

    chat_id = req.chat_id or new_chat_id()
    title = req.title or _autotitle(req)

    existing = await registry.get(chat_id)
    if existing is not None and not existing.done:
        # Already running — just attach a fresh subscriber.
        return StreamingResponse(
            _stream_for_subscriber(existing),
            media_type="text/event-stream",
            headers=_SSE_HEADERS,
        )

    # Persist incoming user/system messages first
    await create_chat(chat_id, title)
    for m in req.messages:
        if m.role in {"user", "system"}:
            await append_message(chat_id, m.model_dump())

    req2 = req.model_copy(update={"chat_id": chat_id})
    run = Run(chat_id=chat_id, request=req2)
    await registry.put(run)
    run.task = asyncio.create_task(_drive_run(run))

    return StreamingResponse(
        _stream_for_subscriber(run),
        media_type="text/event-stream",
        headers=_SSE_HEADERS,
    )


_SSE_HEADERS = {
    "Cache-Control": "no-cache, no-transform",
    "X-Accel-Buffering": "no",
    "Connection": "keep-alive",
}


def _sse(payload: dict) -> bytes:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode()


def _autotitle(req: ChatRequest) -> str:
    for m in req.messages:
        if m.role == "user" and m.content.strip():
            t = m.content.strip().splitlines()[0]
            return t[:60] + ("..." if len(t) > 60 else "")
    return "New chat"


async def _stream_for_subscriber(run: Run) -> AsyncIterator[bytes]:
    """Subscribe to a Run and yield SSE bytes. Replays history first."""

    sub = run.subscribe()
    try:
        # Initial heartbeat so reverse proxies flush headers immediately
        yield b": connected\n\n"
        while True:
            if run.done and sub.queue.empty():
                return
            try:
                ev = await asyncio.wait_for(sub.queue.get(), timeout=20.0)
            except asyncio.TimeoutError:
                # Keepalive comment — keeps NAT/proxy connections open
                yield b": keepalive\n\n"
                continue
            yield _sse(ev)
    except asyncio.CancelledError:
        return
    finally:
        run.unsubscribe(sub)


async def _drive_run(run: Run) -> None:
    """Background task: drive `run_agent` and publish events to the run.

    Also handles persistence: assistant + tool messages are written to the DB
    here (not in the SSE handler) so persistence is independent of whether a
    client is connected.
    """

    chat_id = run.chat_id
    assistant_buf: list[str] = []
    pending_tool_calls: list[dict] = []

    def _flush_assistant() -> None:
        """Persist any buffered assistant text/tool_calls as one message."""
        if assistant_buf or pending_tool_calls:
            asyncio.create_task(append_message(chat_id, {
                "role": "assistant",
                "content": "".join(assistant_buf),
                "tool_calls": list(pending_tool_calls),
            }))
            assistant_buf.clear()
            pending_tool_calls.clear()

    def _drain_injections() -> list[Message]:
        out = list(run.pending_injections)
        run.pending_injections.clear()
        return out

    def _is_cancelled() -> bool:
        return run.cancelled

    try:
        async for evt in run_agent(
            run.request,
            inject_drain=_drain_injections,
            is_cancelled=_is_cancelled,
        ):
            kind = evt.get("type")
            if kind == "delta":
                assistant_buf.append(evt.get("text", ""))
            elif kind == "tool_call":
                pending_tool_calls.append({
                    "id": evt.get("id"),
                    "name": evt.get("name"),
                    "input": evt.get("input"),
                })
            elif kind == "tool_result":
                _flush_assistant()
                await append_message(chat_id, {
                    "role": "tool",
                    "content": evt.get("output", ""),
                    "tool_use_id": evt.get("id"),
                    "name": evt.get("name"),
                })
            elif kind in {"done", "error"}:
                _flush_assistant()
            run.publish(evt)
    except asyncio.CancelledError:
        run.publish({"type": "done", "stop_reason": "cancelled"})
    except Exception as exc:  # noqa: BLE001
        logger.exception("agent loop crashed")
        run.publish({"type": "error", "kind": "internal", "message": repr(exc)})
    finally:
        _flush_assistant()
        run.done = True
        # Final sentinel event so reconnects know the run completed
        run.publish({"type": "run_finished"})


@app.get("/v1/chats/{chat_id}/stream")
async def chat_resume_stream(
    chat_id: str,
    _: None = Depends(require_token),
) -> StreamingResponse:
    """Reconnect to a live (or recently completed) run and replay history."""

    run = await registry.get(chat_id)
    if run is None:
        raise HTTPException(status_code=404, detail="no run for this chat")
    return StreamingResponse(
        _stream_for_subscriber(run),
        media_type="text/event-stream",
        headers=_SSE_HEADERS,
    )


