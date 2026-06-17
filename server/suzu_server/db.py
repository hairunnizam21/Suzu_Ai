"""Tiny SQLite layer for chat persistence (async via aiosqlite)."""

from __future__ import annotations

import json
import time
from typing import Any

import aiosqlite

from .config import settings

_SCHEMA = """
CREATE TABLE IF NOT EXISTS chats (
    id          TEXT PRIMARY KEY,
    title       TEXT NOT NULL DEFAULT 'New chat',
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS messages (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id     TEXT NOT NULL,
    role        TEXT NOT NULL,
    content     TEXT NOT NULL DEFAULT '',
    tool_calls  TEXT NOT NULL DEFAULT '[]',
    tool_use_id TEXT,
    name        TEXT,
    created_at  INTEGER NOT NULL,
    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id, id);
"""


async def init_db() -> None:
    async with aiosqlite.connect(settings.db_path) as db:
        await db.executescript(_SCHEMA)
        await db.commit()


async def create_chat(chat_id: str, title: str) -> None:
    now = int(time.time())
    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute(
            "INSERT OR IGNORE INTO chats(id, title, created_at, updated_at) VALUES (?, ?, ?, ?)",
            (chat_id, title, now, now),
        )
        await db.commit()


async def rename_chat(chat_id: str, title: str) -> None:
    now = int(time.time())
    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute(
            "UPDATE chats SET title=?, updated_at=? WHERE id=?",
            (title, now, chat_id),
        )
        await db.commit()


async def delete_chat(chat_id: str) -> None:
    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute("DELETE FROM chats WHERE id=?", (chat_id,))
        await db.commit()


async def touch_chat(chat_id: str) -> None:
    now = int(time.time())
    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute("UPDATE chats SET updated_at=? WHERE id=?", (now, chat_id))
        await db.commit()


async def append_message(chat_id: str, msg: dict[str, Any]) -> None:
    now = int(time.time())
    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute(
            """INSERT INTO messages(chat_id, role, content, tool_calls, tool_use_id, name, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (
                chat_id,
                msg.get("role", "assistant"),
                msg.get("content", ""),
                json.dumps(msg.get("tool_calls", [])),
                msg.get("tool_use_id"),
                msg.get("name"),
                now,
            ),
        )
        await db.execute("UPDATE chats SET updated_at=? WHERE id=?", (now, chat_id))
        await db.commit()


async def list_chats() -> list[dict[str, Any]]:
    async with aiosqlite.connect(settings.db_path) as db, db.execute(
        """SELECT c.id, c.title, c.created_at, c.updated_at,
                      (SELECT COUNT(*) FROM messages m WHERE m.chat_id = c.id) AS cnt
               FROM chats c
               ORDER BY c.updated_at DESC"""
    ) as cur:
        rows = await cur.fetchall()
    return [
        {
            "id": r[0],
            "title": r[1],
            "created_at": r[2],
            "updated_at": r[3],
            "message_count": r[4],
        }
        for r in rows
    ]


async def get_chat(chat_id: str) -> dict[str, Any] | None:
    async with aiosqlite.connect(settings.db_path) as db:
        async with db.execute(
            "SELECT id, title, created_at, updated_at FROM chats WHERE id=?",
            (chat_id,),
        ) as cur:
            row = await cur.fetchone()
        if not row:
            return None
        async with db.execute(
            """SELECT role, content, tool_calls, tool_use_id, name, created_at
               FROM messages WHERE chat_id=? ORDER BY id""",
            (chat_id,),
        ) as cur:
            mrows = await cur.fetchall()
    return {
        "id": row[0],
        "title": row[1],
        "created_at": row[2],
        "updated_at": row[3],
        "messages": [
            {
                "role": m[0],
                "content": m[1],
                "tool_calls": json.loads(m[2] or "[]"),
                "tool_use_id": m[3],
                "name": m[4],
                "created_at": m[5],
            }
            for m in mrows
        ],
    }
