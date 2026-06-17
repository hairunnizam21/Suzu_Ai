"""Filesystem tools — read, write, edit, grep, ls."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path
from typing import Any

from ..config import settings
from .base import ToolContext, ToolResult, ToolSpec, resolve_in_workspace


async def _read(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    path = args.get("path")
    if not isinstance(path, str):
        return ToolResult(output="error: missing `path`", is_error=True)
    try:
        p = resolve_in_workspace(ctx.workspace, path)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not p.exists():
        return ToolResult(output=f"error: file not found: {p}", is_error=True)
    if not p.is_file():
        return ToolResult(output=f"error: not a file: {p}", is_error=True)
    data = p.read_bytes()[: settings.file_max_read_bytes]
    text = data.decode("utf-8", errors="replace")
    lines = text.splitlines()
    start = int(args.get("offset", 0))
    limit = int(args.get("limit", 2000))
    sliced = lines[start : start + limit]
    rendered = "\n".join(f"{i + 1 + start:6d}\t{ln}" for i, ln in enumerate(sliced))
    suffix = ""
    if start + limit < len(lines):
        suffix = f"\n\n[truncated — file has {len(lines)} lines, showing {start}..{start + len(sliced)}]"
    return ToolResult(
        output=rendered + suffix,
        metadata={"path": str(p), "total_lines": len(lines)},
    )


async def _write(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    path = args.get("path")
    content = args.get("content")
    if not isinstance(path, str) or not isinstance(content, str):
        return ToolResult(output="error: `path` and `content` required", is_error=True)
    try:
        p = resolve_in_workspace(ctx.workspace, path)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    return ToolResult(
        output=f"wrote {len(content)} bytes to {p}",
        metadata={"path": str(p), "bytes": len(content)},
    )


async def _edit(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    path = args.get("path")
    old = args.get("old_string")
    new = args.get("new_string")
    if not isinstance(path, str) or not isinstance(old, str) or not isinstance(new, str):
        return ToolResult(output="error: `path`, `old_string`, `new_string` required", is_error=True)
    try:
        p = resolve_in_workspace(ctx.workspace, path)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not p.exists():
        return ToolResult(output=f"error: file not found: {p}", is_error=True)
    text = p.read_text(encoding="utf-8", errors="replace")
    count = text.count(old)
    if count == 0:
        return ToolResult(output=f"error: `old_string` not found in {p}", is_error=True)
    if count > 1 and not args.get("replace_all"):
        return ToolResult(
            output=f"error: `old_string` appears {count} times — pass replace_all=true or provide a more unique snippet",
            is_error=True,
        )
    new_text = text.replace(old, new) if args.get("replace_all") else text.replace(old, new, 1)
    p.write_text(new_text, encoding="utf-8")
    return ToolResult(
        output=f"edited {p}: {count} occurrence(s) replaced",
        metadata={"path": str(p), "occurrences": count},
    )


async def _grep(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    pattern = args.get("pattern")
    path = args.get("path", ".")
    if not isinstance(pattern, str):
        return ToolResult(output="error: missing `pattern`", is_error=True)
    try:
        p = resolve_in_workspace(ctx.workspace, path)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    glob = args.get("glob")
    cmd = ["rg", "--no-heading", "--line-number", "--color=never"]
    if glob:
        cmd += ["--glob", glob]
    cmd += ["-e", pattern, str(p)]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=60, check=False)
    except FileNotFoundError:
        # Fallback to python regex if ripgrep missing
        return _grep_python(p, pattern)
    out = proc.stdout or proc.stderr
    if not out:
        out = "(no matches)"
    return ToolResult(output=out[: settings.shell_max_output_bytes])


def _grep_python(root: Path, pattern: str) -> ToolResult:
    rx = re.compile(pattern)
    lines: list[str] = []
    files = [root] if root.is_file() else [f for f in root.rglob("*") if f.is_file()]
    for f in files:
        try:
            text = f.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for i, ln in enumerate(text.splitlines(), 1):
            if rx.search(ln):
                lines.append(f"{f}:{i}:{ln}")
                if len(lines) >= 500:
                    lines.append("[truncated at 500 matches]")
                    return ToolResult(output="\n".join(lines))
    return ToolResult(output="\n".join(lines) if lines else "(no matches)")


async def _ls(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    path = args.get("path", ".")
    try:
        p = resolve_in_workspace(ctx.workspace, path)
    except ValueError as exc:
        return ToolResult(output=f"error: {exc}", is_error=True)
    if not p.exists():
        return ToolResult(output=f"error: not found: {p}", is_error=True)
    if p.is_file():
        return ToolResult(output=f"file: {p} ({p.stat().st_size} bytes)")
    entries = []
    for child in sorted(p.iterdir()):
        kind = "d" if child.is_dir() else "f"
        size = "-" if child.is_dir() else child.stat().st_size
        entries.append(f"{kind}\t{size}\t{child.name}")
    return ToolResult(output="\n".join(entries) if entries else "(empty)")


READ_TOOL = ToolSpec(
    name="read",
    description="Read a UTF-8 text file from the workspace. Returns numbered lines.",
    input_schema={
        "type": "object",
        "properties": {
            "path": {"type": "string"},
            "offset": {"type": "integer", "description": "0-based line offset"},
            "limit": {"type": "integer", "description": "Max lines to return (default 2000)"},
        },
        "required": ["path"],
    },
    executor=_read,
)

WRITE_TOOL = ToolSpec(
    name="write",
    description="Write a file (overwrites if exists). Creates parent directories.",
    input_schema={
        "type": "object",
        "properties": {
            "path": {"type": "string"},
            "content": {"type": "string"},
        },
        "required": ["path", "content"],
    },
    executor=_write,
)

EDIT_TOOL = ToolSpec(
    name="edit",
    description=(
        "Replace `old_string` with `new_string` in a file. Default is single "
        "replacement (errors if `old_string` appears more than once unless "
        "replace_all=true)."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "path": {"type": "string"},
            "old_string": {"type": "string"},
            "new_string": {"type": "string"},
            "replace_all": {"type": "boolean"},
        },
        "required": ["path", "old_string", "new_string"],
    },
    executor=_edit,
)

GREP_TOOL = ToolSpec(
    name="grep",
    description="Search files for a regex pattern. Uses ripgrep if available.",
    input_schema={
        "type": "object",
        "properties": {
            "pattern": {"type": "string"},
            "path": {"type": "string", "description": "File or directory to search (default workspace)"},
            "glob": {"type": "string", "description": "Optional filename glob like '*.kt'"},
        },
        "required": ["pattern"],
    },
    executor=_grep,
)

LS_TOOL = ToolSpec(
    name="ls",
    description="List files in a directory (one per line, with type & size).",
    input_schema={
        "type": "object",
        "properties": {"path": {"type": "string", "description": "Directory path (default workspace root)"}},
    },
    executor=_ls,
)

FILE_TOOLS = [READ_TOOL, WRITE_TOOL, EDIT_TOOL, GREP_TOOL, LS_TOOL]


__all__ = ["FILE_TOOLS"]
