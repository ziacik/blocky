#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.ziacik.blocky"

cd "$ROOT_DIR"

./gradlew assembleDebug

if ! command -v adb >/dev/null 2>&1; then
	echo "adb not found in PATH." >&2
	exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
	echo "No Android device/emulator connected." >&2
	exit 1
fi

adb install -r "$APK"
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Bločky deployed."
