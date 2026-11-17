#!/bin/bash
# Boots the hydra_test emulator with a window, then builds, installs and opens HydraChat.
# Usage: ./run-emulator.sh [--fresh]   (--fresh wipes the app data to replay onboarding)

set -euo pipefail

readonly SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
readonly AVD_NAME="hydra_test"
readonly APP_ID="com.hydrachat.app"
readonly BOOT_TIMEOUT_SECONDS=180
readonly ADB="$SDK_DIR/platform-tools/adb"
readonly EMULATOR="$SDK_DIR/emulator/emulator"
readonly PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

for tool in "$ADB" "$EMULATOR"; do
    if ! command -v "$tool" > /dev/null; then
        echo "Missing $tool - is the Android SDK installed in $SDK_DIR?" >&2
        exit 1
    fi
done

if [[ -z "${JAVA_HOME:-}" ]]; then
    java_dir="$(find "$HOME/.jdks" -maxdepth 1 -name 'jdk-17*' | head -n 1)"
    export JAVA_HOME="$java_dir"
fi

if ! "$ADB" devices | grep -q '^emulator-'; then
    echo "Starting emulator $AVD_NAME..."
    nohup "$EMULATOR" -avd "$AVD_NAME" -no-snapshot -no-boot-anim > /dev/null 2>&1 &
fi

"$ADB" wait-for-device
waited_seconds=0
until [[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; do
    if (( waited_seconds >= BOOT_TIMEOUT_SECONDS )); then
        echo "Emulator did not finish booting in ${BOOT_TIMEOUT_SECONDS}s" >&2
        exit 1
    fi
    sleep 2
    waited_seconds=$((waited_seconds + 2))
done

(cd "$PROJECT_DIR" && ./gradlew assembleDebug -q)
"$ADB" install -r "$APK_PATH"

if [[ "${1:-}" == "--fresh" ]]; then
    "$ADB" shell pm clear "$APP_ID"
fi

"$ADB" shell am start -n "$APP_ID/.ui.MainActivity" > /dev/null
echo "HydraChat is running on the emulator."
