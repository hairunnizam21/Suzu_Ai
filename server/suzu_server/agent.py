"""Agent loop — drive a provider until it stops calling tools."""

from __future__ import annotations

import logging
import uuid
from collections.abc import AsyncIterator
from typing import Any

from .config import settings
from .providers import (
    AssistantTextDelta,
    AssistantToolUse,
    AssistantTurnDone,
    ProviderError,
    ProviderExhausted,
    stream_completion,
)
from .remote import SshExecutor
from .schemas import ChatRequest, Message
from .tools import ALL_TOOLS, get_tool, tool_names
from .tools.base import ToolContext

logger = logging.getLogger("suzu.agent")


DEFAULT_SYSTEM_PROMPT = """You are Suzu_Ai, a Devin-style coding assistant running on a small private \
server for a single user. You have full shell access inside a session workspace. Be terse, decisive, \
and prefer to USE TOOLS rather than describing what you would do. When the user asks for code changes, \
use `read`, then `edit` or `write`. When they ask to build/install things, use `shell`. When they ask \
about an APK, use `apk_decompile` (apktool to modify, jadx to read). Always commit progress with \
`shell` (git add/commit) where reasonable. If a tool errors, read the message carefully and try a \
fix before asking the user."""


async def run_agent(req: ChatRequest) -> AsyncIterator[dict[str, Any]]:
    """Yield SSE-shaped events: dicts that the HTTP layer renders as `data: <json>\\n\\n`."""

    chat_id = req.chat_id or uuid.uuid4().hex
    yield {"type": "chat", "chat_id": chat_id}

    # Build per-session workspace
    workspace = settings.workspace_root / chat_id
    workspace.mkdir(parents=True, exist_ok=True)

    # Tool specs
    if req.enabled_tools is None:
        enabled = ALL_TOOLS
    else:
        enabled = [t for t in ALL_TOOLS if t.name in set(req.enabled_tools)]
    tool_specs_for_llm = [
        {"name": t.name, "description": t.description, "input_schema": t.input_schema}
        for t in enabled
    ]

    messages: list[Message] = list(req.messages)
    system = req.system_prompt or DEFAULT_SYSTEM_PROMPT
    max_iter = max(1, min(req.max_iterations, 500))

    iteration = 0
    while iteration < max_iter:
        iteration += 1
        yield {"type": "iteration", "n": iteration, "max": max_iter}

        # Stream the next assistant turn
        text_buf: list[str] = []
        tool_uses: list[AssistantToolUse] = []
        stop_reason: str | None = None
        try:
            async for ev in stream_completion(
                profile=req.provider,
                system_prompt=system,
                messages=messages,
                tool_specs=tool_specs_for_llm,
                max_tokens=req.max_tokens,
                temperature=req.temperature,
            ):
                if isinstance(ev, AssistantTextDelta):
                    text_buf.append(ev.text)
                    yield {"type": "delta", "text": ev.text}
                elif isinstance(ev, AssistantToolUse):
                    tool_uses.append(ev)
                    yield {
                        "type": "tool_call",
                        "id": ev.id,
                        "name": ev.name,
                        "input": ev.input,
                    }
                elif isinstance(ev, AssistantTurnDone):
                    stop_reason = ev.stop_reason
        except ProviderExhausted as exc:
            yield {"type": "error", "kind": "provider_exhausted", "message": str(exc)}
            return
        except ProviderError as exc:
            yield {"type": "error", "kind": "provider_error", "message": str(exc)}
            return

        # Record assistant message
        assistant_msg = Message(
            role="assistant",
            content="".join(text_buf),
            tool_calls=[
                {"id": tu.id, "name": tu.name, "input": tu.input} for tu in tool_uses
            ],
        )
        messages.append(assistant_msg)

        # If no tools were requested, we're done
        if not tool_uses:
            yield {"type": "done", "stop_reason": stop_reason or "stop"}
            return

        # Execute tools — pass through the optional SSH executor so the shell
        # tool can pick it up and run commands against the user's own box.
        ssh_executor: SshExecutor | None = None
        if req.ssh_target is not None:
            ssh_executor = SshExecutor(
                target=req.ssh_target,
                max_output_bytes=settings.shell_max_output_bytes,
            )
        ctx = ToolContext(
            session_id=chat_id,
            workspace=workspace,
            on_preview=_make_preview_callback(),
            extra={"ssh_executor": ssh_executor} if ssh_executor else {},
        )
        for tu in tool_uses:
            tool = get_tool(tu.name)
            if tool is None:
                result_text = f"error: unknown tool {tu.name!r}. Available: {', '.join(tool_names())}"
                is_error = True
            else:
                try:
                    res = await tool.executor(ctx, tu.input or {})
                    result_text = res.output
                    is_error = res.is_error
                except Exception as exc:  # noqa: BLE001
                    logger.exception("tool %s crashed", tu.name)
                    result_text = f"tool {tu.name} crashed: {exc!r}"
                    is_error = True
            yield {
                "type": "tool_result",
                "id": tu.id,
                "name": tu.name,
                "output": result_text,
                "is_error": is_error,
            }
            messages.append(Message(role="tool", content=result_text, tool_use_id=tu.id, name=tu.name))

    yield {
        "type": "error",
        "kind": "max_iterations",
        "message": f"reached max_iterations={max_iter}; bump it from the client settings if you need more.",
    }


def _make_preview_callback():
    # Place-holder hook; preview events are streamed by the tool executor itself
    # via context.on_preview when wired up to the SSE response — kept here for
    # symmetry. The current design pushes one tool_result event at the end and
    # relies on the model to ask for the next step.
    return None


__all__ = ["run_agent"]
