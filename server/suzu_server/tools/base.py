"""Tool primitives."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


@dataclass
class ToolContext:
    """Per-execution context passed to every tool."""

    session_id: str
    workspace: Path
    """Absolute path to the agent's per-session sandbox."""

    on_preview: Callable[[str, dict[str, Any]], Awaitable[None]] | None = None
    """Optional async callback to push live progress to the client."""

    extra: dict[str, Any] = field(default_factory=dict)


@dataclass
class ToolResult:
    output: str
    is_error: bool = False
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class ToolSpec:
    name: str
    description: str
    input_schema: dict[str, Any]
    """JSON Schema describing the tool's inputs (Anthropic-compatible)."""

    executor: Callable[[ToolContext, dict[str, Any]], Awaitable[ToolResult]]


def resolve_in_workspace(workspace: Path, path: str) -> Path:
    """Resolve `path` against the workspace and refuse anything that escapes it.

    Accepts both absolute and relative paths. Absolute paths must already be
    inside the workspace. Symlinks are not followed for the safety check.
    """

    p = Path(path)
    if not p.is_absolute():
        p = workspace / p
    p_resolved = p.resolve()
    ws_resolved = workspace.resolve()
    try:
        p_resolved.relative_to(ws_resolved)
    except ValueError as exc:
        raise ValueError(f"path {path!r} escapes workspace {ws_resolved}") from exc
    return p_resolved
