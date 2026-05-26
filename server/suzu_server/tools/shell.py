"""`shell` tool — run a command in the session workspace."""

from __future__ import annotations

import asyncio
from typing import Any

from ..config import settings
from .base import ToolContext, ToolResult, ToolSpec


async def _run(ctx: ToolContext, args: dict[str, Any]) -> ToolResult:
    command = args.get("command")
    if not isinstance(command, str) or not command.strip():
        return ToolResult(output="error: missing or empty `command`", is_error=True)
    timeout = int(args.get("timeout", settings.shell_timeout_seconds))
    timeout = max(1, min(timeout, settings.shell_timeout_seconds))
    cwd_arg = args.get("cwd")
    cwd = ctx.workspace if not cwd_arg else (ctx.workspace / cwd_arg)
    cwd.mkdir(parents=True, exist_ok=True)

    if ctx.on_preview:
        await ctx.on_preview("shell", {"phase": "start", "command": command, "cwd": str(cwd)})

    proc = await asyncio.create_subprocess_shell(
        command,
        cwd=str(cwd),
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
    )

    try:
        out_bytes = await asyncio.wait_for(proc.stdout.read(settings.shell_max_output_bytes), timeout=timeout) if proc.stdout else b""
        rc = await asyncio.wait_for(proc.wait(), timeout=timeout)
    except TimeoutError:
        proc.kill()
        await proc.wait()
        return ToolResult(output=f"error: command timed out after {timeout}s", is_error=True)

    out = out_bytes.decode("utf-8", errors="replace")
    if len(out_bytes) >= settings.shell_max_output_bytes:
        out += f"\n\n[truncated at {settings.shell_max_output_bytes} bytes]"

    if ctx.on_preview:
        await ctx.on_preview("shell", {"phase": "end", "exit_code": rc, "bytes": len(out_bytes)})

    return ToolResult(
        output=f"$ {command}\nexit={rc}\n{out}",
        is_error=(rc != 0),
        metadata={"exit_code": rc, "command": command},
    )


SHELL_TOOL = ToolSpec(
    name="shell",
    description=(
        "Run a shell command in the session's workspace. Uses /bin/sh -c. "
        "Stdout and stderr are merged. Returns exit code and combined output. "
        "Use this for builds, git operations, package installs, etc."
    ),
    input_schema={
        "type": "object",
        "properties": {
            "command": {"type": "string", "description": "Shell command to run"},
            "cwd": {
                "type": "string",
                "description": "Optional working directory, relative to workspace",
            },
            "timeout": {
                "type": "integer",
                "description": "Max seconds before the command is killed (default 600)",
            },
        },
        "required": ["command"],
    },
    executor=_run,
)
