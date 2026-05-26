"""Pydantic request / response schemas exposed to the Android client."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

Role = Literal["user", "assistant", "system", "tool"]
ProviderKind = Literal["anthropic", "openai_compat"]


class ProviderProfile(BaseModel):
    """One provider definition sent by the client per request.

    The client keeps the list of profiles locally and sends only the active one
    (plus its rotating API keys) on each request — the server never persists
    API keys.
    """

    id: str
    name: str
    kind: ProviderKind
    base_url: str | None = None
    model: str
    api_keys: list[str] = Field(default_factory=list, description="Tried in order; rotates on 401/402/429")
    extra_headers: dict[str, str] = Field(default_factory=dict)


class Message(BaseModel):
    role: Role
    content: str = ""
    tool_calls: list[dict[str, Any]] = Field(default_factory=list)
    tool_use_id: str | None = None
    name: str | None = None
    created_at: int | None = None


SshAuthMode = Literal["password", "key"]


class SshTarget(BaseModel):
    """Optional SSH target. When set, the `shell` tool executes commands over
    SSH against this host instead of running on the server's local filesystem.

    Credentials live only in memory for the duration of the request — they are
    never written to disk by the server.
    """

    host: str
    port: int = 22
    user: str
    auth_mode: SshAuthMode = "password"
    password: str | None = None
    private_key: str | None = None
    workspace: str | None = None


class ChatRequest(BaseModel):
    chat_id: str | None = Field(
        default=None,
        description="If omitted, server creates a new chat and returns its id in the first SSE event.",
    )
    title: str | None = None
    provider: ProviderProfile
    system_prompt: str | None = None
    max_iterations: int = 100
    max_tokens: int | None = Field(
        default=None,
        description="When null, the provider's own default is used (Unlimited toggle from the client).",
    )
    temperature: float = 0.7
    enabled_tools: list[str] | None = Field(
        default=None,
        description="If null, all registered tools are enabled.",
    )
    ssh_target: SshTarget | None = None
    messages: list[Message]


class ChatSummary(BaseModel):
    id: str
    title: str
    created_at: int
    updated_at: int
    message_count: int


class ChatDetail(BaseModel):
    id: str
    title: str
    created_at: int
    updated_at: int
    messages: list[Message]


class RenameRequest(BaseModel):
    title: str


class ConfigResponse(BaseModel):
    server_version: str
    default_model: str
    default_max_iterations: int
    available_tools: list[str]


class ErrorResponse(BaseModel):
    error: str
    detail: str | None = None
