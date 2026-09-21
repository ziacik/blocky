#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.ziacik.blocky"
TARGET=""

usage() {
	cat <<'EOF'
Usage:
	./scripts/deploy.sh [-s SERIAL]
	./scripts/deploy.sh [--target SERIAL]
	./scripts/deploy.sh --list

Options:
	-s, --target SERIAL	Deploy to a specific adb device/emulator.
	--list			List connected adb devices.
	-h, --help		Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		-s|--target)
			if [[ $# -lt 2 ]]; then
				echo "Missing value for $1." >&2
				usage >&2
				exit 2
			fi
			TARGET="$2"
			shift 2
			;;
		--list)
			adb devices -l
			exit 0
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

cd "$ROOT_DIR"

if ! command -v adb >/dev/null 2>&1; then
	echo "adb not found in PATH." >&2
	exit 1
fi

mapfile -t DEVICES < <(
	adb devices |
		awk 'NR > 1 && $2 == "device" { print $1 }'
)

if [[ -n "$TARGET" ]]; then
	if ! printf '%s\n' "${DEVICES[@]}" | grep -Fxq "$TARGET"; then
		echo "Target '$TARGET' is not connected and ready." >&2
		echo "Available devices:" >&2
		if [[ ${#DEVICES[@]} -eq 0 ]]; then
			echo "  (none)" >&2
		else
			printf '  %s\n' "${DEVICES[@]}" >&2
		fi
		exit 1
	fi
elif [[ ${#DEVICES[@]} -eq 0 ]]; then
	echo "No Android device/emulator connected." >&2
	exit 1
elif [[ ${#DEVICES[@]} -eq 1 ]]; then
	TARGET="${DEVICES[0]}"
else
	echo "Multiple Android devices/emulators connected:"
	echo
	for i in "${!DEVICES[@]}"; do
		printf '  %d) %s\n' "$((i + 1))" "${DEVICES[$i]}"
	done
	echo

	while true; do
		read -r -p "Select target [1-${#DEVICES[@]}]: " SELECTION
		if [[ "$SELECTION" =~ ^[0-9]+$ ]] &&
			(( SELECTION >= 1 && SELECTION <= ${#DEVICES[@]} )); then
			TARGET="${DEVICES[$((SELECTION - 1))]}"
			break
		fi
		echo "Invalid selection."
	done
fi

./gradlew assembleDebug

ADB=(adb -s "$TARGET")

"${ADB[@]}" install -r "$APK"
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

echo "Bločky deployed to $TARGET."
