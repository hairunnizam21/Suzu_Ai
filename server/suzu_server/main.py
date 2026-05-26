"""FastAPI entrypoint."""

from __future__ import annotations

import json
import logging
import uuid
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
from .schemas import (
    ChatDetail,
    ChatRequest,
    ChatSummary,
    ConfigResponse,
    RenameRequest,
)
from .tools import tool_names

logger = logging.getLogger("suzu.main")


@asynccontextmanager
async def lifespan(app: FastAPI):  # noqa: D401
    await init_db()
    settings.workspace_root.mkdir(parents=True, exist_ok=True)
    logger.info("Suzu server %s starting on %s:%s", __version__, settings.host, settings.port)
    yield


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
    await delete_chat(chat_id)
    return {"status": "ok"}


@app.post("/v1/chat")
async def chat_stream(
    req: ChatRequest,
    _: None = Depends(require_token),
) -> StreamingResponse:
    """SSE endpoint — streams agent events.

    Important: this endpoint *does not* persist the user/assistant messages in
    the DB until the turn finishes successfully. Persistence happens inside the
    generator below so a network drop doesn't half-write state.
    """

    chat_id = req.chat_id or uuid.uuid4().hex
    title = req.title or _autotitle(req)

    async def event_stream() -> AsyncIterator[bytes]:
        await create_chat(chat_id, title)
        # Persist incoming user messages first
        for m in req.messages:
            if m.role in {"user", "system"}:
                await append_message(chat_id, m.model_dump())

        # Pass through the agent loop
        req2 = req.model_copy(update={"chat_id": chat_id})
        assistant_buf: list[str] = []
        pending_tool_calls: list[dict] = []
        try:
            async for evt in run_agent(req2):
                # Persist completed assistant / tool messages on the fly
                if evt.get("type") == "delta":
                    assistant_buf.append(evt.get("text", ""))
                elif evt.get("type") == "tool_call":
                    pending_tool_calls.append({
                        "id": evt.get("id"),
                        "name": evt.get("name"),
                        "input": evt.get("input"),
                    })
                elif evt.get("type") == "tool_result":
                    # Flush assistant message that produced the tool calls
                    if assistant_buf or pending_tool_calls:
                        await append_message(chat_id, {
                            "role": "assistant",
                            "content": "".join(assistant_buf),
                            "tool_calls": pending_tool_calls,
                        })
                        assistant_buf = []
                        pending_tool_calls = []
                    await append_message(chat_id, {
                        "role": "tool",
                        "content": evt.get("output", ""),
                        "tool_use_id": evt.get("id"),
                        "name": evt.get("name"),
                    })
                elif evt.get("type") in {"done", "error"} and (assistant_buf or pending_tool_calls):
                    await append_message(chat_id, {
                        "role": "assistant",
                        "content": "".join(assistant_buf),
                        "tool_calls": pending_tool_calls,
                    })
                    assistant_buf = []
                    pending_tool_calls = []
                yield _sse(evt)
        except Exception as exc:  # noqa: BLE001
            logger.exception("agent loop crashed")
            yield _sse({"type": "error", "kind": "internal", "message": repr(exc)})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


def _sse(payload: dict) -> bytes:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n".encode()


def _autotitle(req: ChatRequest) -> str:
    for m in req.messages:
        if m.role == "user" and m.content.strip():
            t = m.content.strip().splitlines()[0]
            return t[:60] + ("..." if len(t) > 60 else "")
    return "New chat"
