"""Optional remote transports for tool execution."""

from .ssh import SshExecutor, SshRunResult

__all__ = ["SshExecutor", "SshRunResult"]
