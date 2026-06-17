"""Per-chat attachment storage.

Each chat workspace gets an `attachments/` subdirectory. Uploaded files are
stored under `attachments/<id>/<original-filename>` so the agent can reference
them with stable paths regardless of how the user named the file.

The agent prompt includes a short summary of attached files for the current
turn. The actual contents are read on demand via the `read` tool — we don't
inline raw bytes into the LLM context.
"""

from __future__ import annotations

import mimetypes
import secrets
from dataclasses import dataclass
from pathlib import Path

from .config import settings


def _safe_filename(name: str) -> str:
    """Strip path separators and weird characters; default if blank."""
    base = Path(name).name
    cleaned = "".join(c for c in base if c.isalnum() or c in "._- ")
    cleaned = cleaned.strip().replace(" ", "_")
    return cleaned or "upload.bin"


@dataclass
class StoredAttachment:
    id: str
    filename: str
    mime_type: str
    size_bytes: int
    absolute_path: Path
    relative_path: str

    def as_dict(self) -> dict:
        return {
            "id": self.id,
            "filename": self.filename,
            "mime_type": self.mime_type,
            "size_bytes": self.size_bytes,
            "relative_path": self.relative_path,
        }


def attachments_dir(chat_id: str) -> Path:
    return settings.workspace_root / chat_id / "attachments"


async def save_attachment(
    chat_id: str,
    filename: str,
    content: bytes,
    mime_type: str | None = None,
) -> StoredAttachment:
    """Persist `content` under the chat workspace and return its descriptor."""

    safe_name = _safe_filename(filename)
    attachment_id = secrets.token_urlsafe(8)
    base = attachments_dir(chat_id) / attachment_id
    base.mkdir(parents=True, exist_ok=True)
    abs_path = base / safe_name
    abs_path.write_bytes(content)

    if not mime_type:
        guess, _ = mimetypes.guess_type(safe_name)
        mime_type = guess or "application/octet-stream"

    rel = f"attachments/{attachment_id}/{safe_name}"
    return StoredAttachment(
        id=attachment_id,
        filename=safe_name,
        mime_type=mime_type,
        size_bytes=len(content),
        absolute_path=abs_path,
        relative_path=rel,
    )


def resolve_path(chat_id: str, relative_path: str) -> Path | None:
    """Translate a chat-relative path to an absolute path, refusing escapes."""

    base = (settings.workspace_root / chat_id).resolve()
    candidate = (base / relative_path).resolve()
    try:
        candidate.relative_to(base)
    except ValueError:
        return None
    if not candidate.exists() or not candidate.is_file():
        return None
    return candidate


__all__ = ["StoredAttachment", "attachments_dir", "resolve_path", "save_attachment"]
