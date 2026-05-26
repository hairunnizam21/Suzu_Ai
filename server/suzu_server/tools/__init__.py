"""Built-in tool implementations + registry.

Every tool conforms to :class:`ToolSpec` — schema for the LLM, async executor,
and an optional streaming preview hook so the phone can show progress live.
"""

from __future__ import annotations

from .apk import APK_TOOLS
from .base import ToolContext, ToolResult, ToolSpec
from .file import FILE_TOOLS
from .shell import SHELL_TOOL

ALL_TOOLS: list[ToolSpec] = [
    SHELL_TOOL,
    *FILE_TOOLS,
    *APK_TOOLS,
]


def get_tool(name: str) -> ToolSpec | None:
    for t in ALL_TOOLS:
        if t.name == name:
            return t
    return None


def tool_names() -> list[str]:
    return [t.name for t in ALL_TOOLS]


__all__ = ["ToolSpec", "ToolContext", "ToolResult", "ALL_TOOLS", "get_tool", "tool_names"]
