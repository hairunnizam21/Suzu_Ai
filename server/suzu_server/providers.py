"""Provider adapters with auto-rotation of API keys.

Supports two protocol families:

- `anthropic`        — native Anthropic Messages API (incl. `tool_use` blocks)
- `openai_compat`    — OpenAI-compatible chat completions (OpenAI, OpenRouter,
                       Together, Groq, AfiqStoreAPI, ...)

Both adapters yield a uniform stream of typed events:

    AssistantTextDelta(text)
    AssistantToolUse(id, name, input)
    AssistantTurnDone(stop_reason)

The agent loop in `agent.py` translates these into protocol-agnostic state.

Quota / auth failures (`401`, `402`, `429`) cause the adapter to rotate to the
next API key and re-issue the request transparently. If every key is exhausted
the call raises `ProviderExhausted`.
"""

from __future__ import annotations

import asyncio
import json
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

import httpx

from .schemas import Message, ProviderProfile


class ProviderError(Exception):
    pass


class ProviderExhausted(ProviderError):
    """All API keys returned 401/402/429."""


@dataclass
class AssistantTextDelta:
    text: str


@dataclass
class AssistantToolUse:
    id: str
    name: str
    input: dict[str, Any]


@dataclass
class AssistantTurnDone:
    stop_reason: str | None


ProviderEvent = AssistantTextDelta | AssistantToolUse | AssistantTurnDone


_ROTATING_STATUSES = {401, 402, 403, 429}


async def stream_completion(
    profile: ProviderProfile,
    system_prompt: str | None,
    messages: list[Message],
    tool_specs: list[dict[str, Any]],
    max_tokens: int = 4096,
    temperature: float = 0.7,
) -> AsyncIterator[ProviderEvent]:
    """Stream one assistant turn from the provider, rotating keys on failure."""

    if not profile.api_keys:
        raise ProviderError("provider profile has no API keys")

    last_error: Exception | None = None
    for _key_idx, key in enumerate(profile.api_keys):
        try:
            if profile.kind == "anthropic":
                async for ev in _stream_anthropic(profile, key, system_prompt, messages, tool_specs, max_tokens, temperature):
                    yield ev
            else:
                async for ev in _stream_openai_compat(profile, key, system_prompt, messages, tool_specs, max_tokens, temperature):
                    yield ev
            return  # success
        except _RotateKey as exc:
            last_error = exc
            continue

    raise ProviderExhausted(
        f"all {len(profile.api_keys)} api keys exhausted for provider {profile.name!r}: {last_error}"
    )


class _RotateKey(Exception):
    pass


# ---------------------------------------------------------------------------
# Anthropic native
# ---------------------------------------------------------------------------


async def _stream_anthropic(
    profile: ProviderProfile,
    api_key: str,
    system_prompt: str | None,
    messages: list[Message],
    tool_specs: list[dict[str, Any]],
    max_tokens: int,
    temperature: float,
) -> AsyncIterator[ProviderEvent]:
    base = (profile.base_url or "https://api.anthropic.com").rstrip("/")
    url = f"{base}/v1/messages"
    headers = {
        "x-api-key": api_key,
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
        **profile.extra_headers,
    }
    body: dict[str, Any] = {
        "model": profile.model,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "stream": True,
        "messages": _anthropic_messages(messages),
    }
    if system_prompt:
        body["system"] = system_prompt
    if tool_specs:
        body["tools"] = [
            {"name": t["name"], "description": t["description"], "input_schema": t["input_schema"]}
            for t in tool_specs
        ]

    async with httpx.AsyncClient(timeout=httpx.Timeout(connect=15.0, read=300.0, write=60.0, pool=15.0)) as client:
        async with client.stream("POST", url, headers=headers, json=body) as resp:
            if resp.status_code in _ROTATING_STATUSES:
                raise _RotateKey(f"anthropic {resp.status_code}")
            if resp.status_code >= 400:
                err = await resp.aread()
                raise ProviderError(f"anthropic {resp.status_code}: {err.decode(errors='replace')[:500]}")

            current_tool: dict[str, Any] | None = None
            current_tool_json = ""
            stop_reason: str | None = None
            async for line in resp.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                payload = line[5:].strip()
                if not payload:
                    continue
                try:
                    evt = json.loads(payload)
                except json.JSONDecodeError:
                    continue
                t = evt.get("type")
                if t == "content_block_start":
                    cb = evt.get("content_block") or {}
                    if cb.get("type") == "tool_use":
                        current_tool = {
                            "id": cb.get("id", ""),
                            "name": cb.get("name", ""),
                        }
                        current_tool_json = ""
                elif t == "content_block_delta":
                    delta = evt.get("delta") or {}
                    if delta.get("type") == "text_delta":
                        text = delta.get("text") or ""
                        if text:
                            yield AssistantTextDelta(text=text)
                    elif delta.get("type") == "input_json_delta":
                        current_tool_json += delta.get("partial_json") or ""
                elif t == "content_block_stop":
                    if current_tool:
                        try:
                            args = json.loads(current_tool_json) if current_tool_json else {}
                        except json.JSONDecodeError:
                            args = {}
                        yield AssistantToolUse(
                            id=current_tool["id"],
                            name=current_tool["name"],
                            input=args,
                        )
                        current_tool = None
                        current_tool_json = ""
                elif t == "message_delta":
                    delta = evt.get("delta") or {}
                    if "stop_reason" in delta:
                        stop_reason = delta.get("stop_reason")
                elif t == "message_stop":
                    yield AssistantTurnDone(stop_reason=stop_reason)
                    return


def _anthropic_messages(messages: list[Message]) -> list[dict[str, Any]]:
    """Translate our internal Message list into Anthropic's content-block format."""

    out: list[dict[str, Any]] = []
    for m in messages:
        if m.role == "system":
            continue  # system goes top-level
        if m.role == "tool":
            out.append({
                "role": "user",
                "content": [{
                    "type": "tool_result",
                    "tool_use_id": m.tool_use_id or "",
                    "content": m.content,
                }],
            })
            continue
        blocks: list[dict[str, Any]] = []
        if m.content:
            blocks.append({"type": "text", "text": m.content})
        for tc in m.tool_calls:
            blocks.append({
                "type": "tool_use",
                "id": tc.get("id", ""),
                "name": tc.get("name", ""),
                "input": tc.get("input", {}),
            })
        if not blocks:
            blocks = [{"type": "text", "text": ""}]
        out.append({"role": m.role, "content": blocks})
    return out


# ---------------------------------------------------------------------------
# OpenAI-compatible (OpenAI / OpenRouter / AfiqStoreAPI / Together / Groq / ...)
# ---------------------------------------------------------------------------


async def _stream_openai_compat(
    profile: ProviderProfile,
    api_key: str,
    system_prompt: str | None,
    messages: list[Message],
    tool_specs: list[dict[str, Any]],
    max_tokens: int,
    temperature: float,
) -> AsyncIterator[ProviderEvent]:
    base = (profile.base_url or "https://api.openai.com").rstrip("/")
    if not base.endswith("/v1"):
        base = base + "/v1"
    url = f"{base}/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "content-type": "application/json",
        **profile.extra_headers,
    }
    msgs_oa = _openai_messages(system_prompt, messages)
    body: dict[str, Any] = {
        "model": profile.model,
        "messages": msgs_oa,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "stream": True,
    }
    if tool_specs:
        body["tools"] = [
            {
                "type": "function",
                "function": {
                    "name": t["name"],
                    "description": t["description"],
                    "parameters": t["input_schema"],
                },
            }
            for t in tool_specs
        ]
        body["tool_choice"] = "auto"

    async with httpx.AsyncClient(timeout=httpx.Timeout(connect=15.0, read=300.0, write=60.0, pool=15.0)) as client:
        async with client.stream("POST", url, headers=headers, json=body) as resp:
            if resp.status_code in _ROTATING_STATUSES:
                raise _RotateKey(f"openai_compat {resp.status_code}")
            if resp.status_code >= 400:
                err = await resp.aread()
                raise ProviderError(
                    f"openai_compat {resp.status_code}: {err.decode(errors='replace')[:500]}"
                )

            partial_tool_calls: dict[int, dict[str, Any]] = {}
            stop_reason: str | None = None
            async for line in resp.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                payload = line[5:].strip()
                if payload == "[DONE]":
                    # Flush any tool calls before closing
                    for _, tc in sorted(partial_tool_calls.items()):
                        yield _flush_tool_call(tc)
                    yield AssistantTurnDone(stop_reason=stop_reason or "stop")
                    return
                if not payload:
                    continue
                try:
                    evt = json.loads(payload)
                except json.JSONDecodeError:
                    continue
                choices = evt.get("choices") or []
                if not choices:
                    continue
                choice = choices[0] or {}
                if isinstance(choice.get("finish_reason"), str):
                    stop_reason = choice["finish_reason"]
                delta = choice.get("delta") or {}
                content = delta.get("content")
                if isinstance(content, str) and content:
                    yield AssistantTextDelta(text=content)
                tool_calls = delta.get("tool_calls") or []
                for piece in tool_calls:
                    if not isinstance(piece, dict):
                        continue
                    idx = piece.get("index", 0)
                    slot = partial_tool_calls.setdefault(idx, {"id": "", "name": "", "args": ""})
                    if piece.get("id"):
                        slot["id"] = piece["id"]
                    fn = piece.get("function") or {}
                    if fn.get("name"):
                        slot["name"] = fn["name"]
                    if isinstance(fn.get("arguments"), str):
                        slot["args"] += fn["arguments"]


def _flush_tool_call(tc: dict[str, Any]) -> AssistantToolUse:
    try:
        args = json.loads(tc.get("args") or "{}")
    except json.JSONDecodeError:
        args = {}
    return AssistantToolUse(id=tc.get("id") or "", name=tc.get("name") or "", input=args)


def _openai_messages(system_prompt: str | None, messages: list[Message]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    if system_prompt:
        out.append({"role": "system", "content": system_prompt})
    for m in messages:
        if m.role == "system":
            out.append({"role": "system", "content": m.content})
            continue
        if m.role == "tool":
            out.append({
                "role": "tool",
                "tool_call_id": m.tool_use_id or "",
                "content": m.content,
            })
            continue
        if m.role == "assistant" and m.tool_calls:
            out.append({
                "role": "assistant",
                "content": m.content or None,
                "tool_calls": [
                    {
                        "id": tc.get("id", ""),
                        "type": "function",
                        "function": {
                            "name": tc.get("name", ""),
                            "arguments": json.dumps(tc.get("input", {})),
                        },
                    }
                    for tc in m.tool_calls
                ],
            })
            continue
        out.append({"role": m.role, "content": m.content})
    return out


__all__ = [
    "ProviderError",
    "ProviderExhausted",
    "AssistantTextDelta",
    "AssistantToolUse",
    "AssistantTurnDone",
    "ProviderEvent",
    "stream_completion",
]


# Make asyncio.TimeoutError accessible without importing it at top
TimeoutError = asyncio.TimeoutError  # noqa: A001
