"""Optional SSH transport for the `shell` tool.

When the client sends an `ssh_target` in [ChatRequest][suzu_server.schemas.ChatRequest],
the agent's [ToolContext][suzu_server.tools.base.ToolContext] gets an
[SshExecutor][suzu_server.remote.ssh.SshExecutor] attached. The `shell` tool
then routes commands over SSH instead of running them locally.

Connections are short-lived (one per command) on purpose: the agent loop
serialises tool calls anyway, so the extra connection overhead is negligible
and we don't need to track keepalive / reconnect state. Credentials live only
in memory for the duration of the HTTP request.
"""

from __future__ import annotations

import asyncio
import logging
from dataclasses import dataclass

import asyncssh

from ..schemas import SshTarget

logger = logging.getLogger("suzu.ssh")


@dataclass
class SshRunResult:
    stdout: str
    exit_code: int
    timed_out: bool = False
    bytes_read: int = 0


class SshExecutor:
    """Runs a single shell command over SSH against [SshTarget][]."""

    def __init__(self, target: SshTarget, max_output_bytes: int = 2_000_000) -> None:
        self.target = target
        self.max_output_bytes = max_output_bytes

    @property
    def workspace(self) -> str:
        return (self.target.workspace or "~/suzu-workspace").strip() or "~/suzu-workspace"

    def _connect_kwargs(self) -> dict[str, object]:
        kw: dict[str, object] = {
            "host": self.target.host,
            "port": self.target.port or 22,
            "username": self.target.user,
            "known_hosts": None,  # The agent talks to user's own box; trust on first use.
        }
        if self.target.auth_mode == "key" and self.target.private_key:
            kw["client_keys"] = [asyncssh.import_private_key(self.target.private_key)]
        elif self.target.password:
            kw["password"] = self.target.password
        return kw

    async def ensure_workspace(self) -> None:
        """Create the workspace directory on the remote box if missing."""
        await self.run(f"mkdir -p {self._shell_quote(self.workspace)}", timeout=10)

    async def run(self, command: str, *, timeout: int = 600, cwd: str | None = None) -> SshRunResult:
        target_dir = cwd or self.workspace
        wrapped = f"cd {self._shell_quote(target_dir)} && {command}"
        kwargs = self._connect_kwargs()
        try:
            async with asyncssh.connect(**kwargs) as conn:  # type: ignore[arg-type]
                try:
                    proc = await asyncio.wait_for(
                        conn.run(wrapped, check=False),
                        timeout=timeout,
                    )
                except TimeoutError:
                    return SshRunResult(stdout="", exit_code=-1, timed_out=True)

                stdout_part = (proc.stdout or "") + (proc.stderr or "")
                if len(stdout_part) > self.max_output_bytes:
                    truncated = stdout_part[: self.max_output_bytes]
                    truncated += f"\n\n[truncated at {self.max_output_bytes} bytes]"
                    return SshRunResult(
                        stdout=truncated,
                        exit_code=proc.exit_status or 0,
                        bytes_read=len(stdout_part),
                    )
                return SshRunResult(
                    stdout=stdout_part,
                    exit_code=proc.exit_status or 0,
                    bytes_read=len(stdout_part),
                )
        except (OSError, asyncssh.Error) as exc:
            logger.warning("ssh connect/exec failed: %s", exc)
            return SshRunResult(stdout=f"ssh error: {exc}", exit_code=-1)

    @staticmethod
    def _shell_quote(s: str) -> str:
        """Single-quote a string for a POSIX shell."""
        if not s:
            return "''"
        # Escape any embedded single quotes.
        return "'" + s.replace("'", "'\\''") + "'"
