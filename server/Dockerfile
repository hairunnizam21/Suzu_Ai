FROM python:3.11-slim

ENV DEBIAN_FRONTEND=noninteractive \
    ANDROID_HOME=/opt/suzu-tools/android-sdk \
    ANDROID_SDK_ROOT=/opt/suzu-tools/android-sdk \
    PATH=/opt/suzu-tools/android-sdk/cmdline-tools/latest/bin:/opt/suzu-tools/android-sdk/platform-tools:/opt/suzu-tools/android-sdk/build-tools/34.0.0:/usr/local/bin:/usr/bin:/bin

RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        ripgrep curl unzip ca-certificates wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/suzu-tools
RUN mkdir -p android-sdk/cmdline-tools \
    && curl -sL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdline.zip \
    && unzip -q cmdline.zip -d /tmp \
    && mv /tmp/cmdline-tools android-sdk/cmdline-tools/latest \
    && rm cmdline.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null \
    && curl -sL https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar -o apktool.jar \
    && curl -sL https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip -o jadx.zip \
    && unzip -q jadx.zip -d /tmp/jadx && mv /tmp/jadx jadx && rm jadx.zip

WORKDIR /app
COPY pyproject.toml ./
COPY suzu_server ./suzu_server
RUN pip install --upgrade pip wheel && pip install -e .

ENV SUZU_APKTOOL_JAR=/opt/suzu-tools/apktool.jar \
    SUZU_JADX_BIN=/opt/suzu-tools/jadx/bin/jadx \
    SUZU_APKSIGNER_BIN=/opt/suzu-tools/android-sdk/build-tools/34.0.0/apksigner \
    SUZU_ZIPALIGN_BIN=/opt/suzu-tools/android-sdk/build-tools/34.0.0/zipalign \
    SUZU_HOST=0.0.0.0 \
    SUZU_PORT=8765

EXPOSE 8765
CMD ["uvicorn", "suzu_server.main:app", "--host", "0.0.0.0", "--port", "8765"]
