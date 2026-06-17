#!/usr/bin/env bash
# Run the Suzu_Ai server. Run install.sh first (creates ./venv and .env).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -d "$SCRIPT_DIR/venv" ]]; then
    echo "venv not found — run ./install.sh first" >&2
    exit 1
fi

source "$SCRIPT_DIR/venv/bin/activate"

# Load .env into the environment (without printing values)
if [[ -f "$SCRIPT_DIR/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    . "$SCRIPT_DIR/.env"
    set +a
fi

HOST="${SUZU_HOST:-0.0.0.0}"
PORT="${SUZU_PORT:-8765}"

exec uvicorn suzu_server.main:app --host "$HOST" --port "$PORT" --log-level "${SUZU_LOG_LEVEL:-info}"
