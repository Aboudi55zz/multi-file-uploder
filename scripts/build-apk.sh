#!/usr/bin/env bash
set -euo pipefail

VARIANT="${1:-debug}"

# Decode debug keystore from base64 if missing
if [ ! -f debug.keystore ]; then
  if [ -f debug.keystore.base64 ]; then
    base64 -d debug.keystore.base64 > debug.keystore
  else
    keytool -genkey -v \
      -keystore debug.keystore \
      -storepass android \
      -alias androiddebugkey \
      -keypass android \
      -keyalg RSA \
      -keysize 2048 \
      -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US"
  fi
fi

# Ensure .env exists for Secrets Gradle Plugin
if [ ! -f .env ] && [ -f .env.example ]; then
  cp .env.example .env
fi

GRADLE_CMD="gradle"
if [ -x "./gradlew" ]; then
  GRADLE_CMD="./gradlew"
fi

mkdir -p apk-output

if [ "$VARIANT" = "debug" ] || [ "$VARIANT" = "both" ]; then
  "$GRADLE_CMD" :app:assembleDebug --no-daemon
  cp app/build/outputs/apk/debug/*.apk apk-output/FolderPush-debug.apk
  echo "Built: apk-output/FolderPush-debug.apk"
fi

if [ "$VARIANT" = "release" ] || [ "$VARIANT" = "both" ]; then
  if [ -z "${KEYSTORE_PATH:-}" ] || [ -z "${STORE_PASSWORD:-}" ] || [ -z "${KEY_PASSWORD:-}" ]; then
    export KEYSTORE_PATH="/tmp/folderpush-release.jks"
    export STORE_PASSWORD="folderpush123"
    export KEY_PASSWORD="folderpush123"
    if [ ! -f "$KEYSTORE_PATH" ]; then
      keytool -genkey -v \
        -keystore "$KEYSTORE_PATH" \
        -storepass "$STORE_PASSWORD" \
        -alias upload \
        -keypass "$KEY_PASSWORD" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=FolderPush,O=FolderPush,C=US"
    fi
  fi
  "$GRADLE_CMD" :app:assembleRelease --no-daemon
  cp app/build/outputs/apk/release/*.apk apk-output/FolderPush-release.apk
  echo "Built: apk-output/FolderPush-release.apk"
fi
