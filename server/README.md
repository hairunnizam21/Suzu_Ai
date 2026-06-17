# Suzu_Ai server

FastAPI agent backend. Receives chat requests from the Suzu_Ai Android app,
runs an Anthropic / OpenAI-compatible loop with tool use, executes shell /
file / APK reverse-engineering tools in a per-session workspace, streams
events back as SSE.

## Install (Ubuntu 22.04)

```bash
sudo ./install.sh   # installs JDK 17, Android SDK, apktool, jadx, python venv
./run.sh            # starts FastAPI on 0.0.0.0:8765
```

The installer prints a randomly generated `SUZU_TOKEN`. Copy that into the
Android app under **Settings → Server config**.

## Docker

```bash
cp .env.example .env && $EDITOR .env   # set SUZU_TOKEN
docker compose up -d --build
```

## Endpoints

| Method | Path                  | Purpose                                  |
|--------|-----------------------|------------------------------------------|
| GET    | `/health`             | Liveness, returns version                |
| GET    | `/v1/config`          | Tool list + defaults                     |
| GET    | `/v1/chats`           | List chats (id, title, message_count)    |
| GET    | `/v1/chats/{id}`      | Full chat (messages)                     |
| PATCH  | `/v1/chats/{id}`      | Rename chat                              |
| DELETE | `/v1/chats/{id}`      | Delete chat                              |
| POST   | `/v1/chat`            | Stream a new turn (SSE)                  |

All endpoints (except `/health`) require `Authorization: Bearer $SUZU_TOKEN`.

## SSE event types

| `type`         | Payload                                  | Meaning                              |
|----------------|------------------------------------------|--------------------------------------|
| `chat`         | `chat_id`                                | New (or resumed) chat id             |
| `iteration`    | `n`, `max`                               | Agent loop progress                  |
| `delta`        | `text`                                   | Streaming assistant text chunk       |
| `tool_call`    | `id`, `name`, `input`                    | Model decided to call a tool         |
| `tool_result`  | `id`, `name`, `output`, `is_error`       | Tool finished                        |
| `done`         | `stop_reason`                            | Turn ended naturally                 |
| `error`        | `kind`, `message`                        | Provider exhausted, max_iter, etc.   |

## Tools

`shell`, `read`, `write`, `edit`, `grep`, `ls`, `apk_decompile`,
`apk_recompile`, `apk_sign`. Each runs inside the session's sandbox at
`workspace/{chat_id}/` and refuses paths that escape it.

## Provider profiles

Profiles live on the **client**, never on the server. Each request includes:

```jsonc
{
  "provider": {
    "id": "anth-1",
    "name": "Claude",
    "kind": "anthropic",                       // or "openai_compat"
    "base_url": "https://api.anthropic.com",   // optional
    "model": "claude-3-5-sonnet-20241022",
    "api_keys": ["sk-ant-...", "sk-ant-..."],
    "extra_headers": {}
  },
  ...
}
```

Keys are tried in order. `401 / 402 / 429` rotates to the next key and retries
the current turn transparently. If all keys are exhausted you get a
`{"type": "error", "kind": "provider_exhausted"}` SSE event.
