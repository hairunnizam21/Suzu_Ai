#!/usr/bin/env bash
# Suzu_Ai server installer for Ubuntu 22.04 (8 GB RAM, 4 vCPU recommended).
#
# Installs:
#   - JDK 17, Python 3.11, ripgrep, unzip, curl
#   - Android SDK platform-tools, build-tools 34, platform 34
#   - apktool, jadx
#   - Python dependencies inside ./venv
#   - Generates a random SUZU_TOKEN and writes .env
#
# Re-running is safe (idempotent).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

require_root() {
    if [[ $EUID -ne 0 ]]; then
        echo "Please run as root (sudo ./install.sh)" >&2
        exit 1
    fi
}

step() { echo; echo ">>> $*"; }

require_root

step "Installing apt packages"
apt-get update -y
apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless \
    python3.11 python3.11-venv python3.11-dev \
    ripgrep curl unzip ca-certificates wget

step "Setting up /opt/suzu-tools"
mkdir -p /opt/suzu-tools

# Android SDK ----------------------------------------------------------------
if [[ ! -d /opt/suzu-tools/android-sdk ]]; then
    step "Installing Android SDK cmdline-tools"
    mkdir -p /opt/suzu-tools/android-sdk/cmdline-tools
    tmp=$(mktemp -d)
    curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$tmp/cmdline.zip"
    unzip -q "$tmp/cmdline.zip" -d "$tmp"
    mv "$tmp/cmdline-tools" /opt/suzu-tools/android-sdk/cmdline-tools/latest
    rm -rf "$tmp"
fi

export ANDROID_HOME=/opt/suzu-tools/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0:$PATH"

step "Accepting Android SDK licenses"
# `yes | sdkmanager` dies under `set -euo pipefail` because `yes` receives
# SIGPIPE (exit 141) once sdkmanager closes stdin. Disable pipefail just for
# this pipeline, and accept a non-zero exit (licenses already accepted is fine).
set +o pipefail
yes 2>/dev/null | sdkmanager --licenses >/dev/null || true
set -o pipefail

step "Installing Android platform-tools, build-tools;34.0.0, platforms;android-34"
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

# apktool --------------------------------------------------------------------
if [[ ! -f /opt/suzu-tools/apktool.jar ]]; then
    step "Installing apktool 2.9.3"
    curl -sL https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar \
        -o /opt/suzu-tools/apktool.jar
fi

# jadx -----------------------------------------------------------------------
if [[ ! -x /opt/suzu-tools/jadx/bin/jadx ]]; then
    step "Installing jadx 1.5.0"
    tmp=$(mktemp -d)
    curl -sL https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o "$tmp/jadx.zip"
    unzip -q "$tmp/jadx.zip" -d "$tmp/jadx"
    rm -rf /opt/suzu-tools/jadx
    mv "$tmp/jadx" /opt/suzu-tools/jadx
    rm -rf "$tmp"
fi

# Python venv ----------------------------------------------------------------
step "Creating Python venv"
if [[ ! -d "$SCRIPT_DIR/venv" ]]; then
    python3.11 -m venv "$SCRIPT_DIR/venv"
fi
"$SCRIPT_DIR/venv/bin/pip" install --upgrade pip wheel >/dev/null
"$SCRIPT_DIR/venv/bin/pip" install -e "$SCRIPT_DIR" >/dev/null

# .env -----------------------------------------------------------------------
if [[ ! -f "$SCRIPT_DIR/.env" ]]; then
    step "Generating .env with a random SUZU_TOKEN"
    token=$(openssl rand -hex 32 2>/dev/null || head -c 32 /dev/urandom | xxd -p -c 32)
    cat > "$SCRIPT_DIR/.env" <<EOF
SUZU_TOKEN=$token
SUZU_HOST=0.0.0.0
SUZU_PORT=8765
SUZU_APKTOOL_JAR=/opt/suzu-tools/apktool.jar
SUZU_JADX_BIN=/opt/suzu-tools/jadx/bin/jadx
SUZU_APKSIGNER_BIN=$ANDROID_HOME/build-tools/34.0.0/apksigner
SUZU_ZIPALIGN_BIN=$ANDROID_HOME/build-tools/34.0.0/zipalign
EOF
    chmod 600 "$SCRIPT_DIR/.env"
fi

echo
echo "================================================================"
echo "  Installation complete."
echo
echo "  Token (paste this in the Suzu_Ai app Settings → Server config):"
echo
grep '^SUZU_TOKEN=' "$SCRIPT_DIR/.env" | cut -d= -f2-
echo
echo "  Start the server with:  cd $SCRIPT_DIR && ./run.sh"
echo "================================================================"
