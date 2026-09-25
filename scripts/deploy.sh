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

DEVICE_SERIALS=()
DEVICE_LABELS=()

while IFS= read -r line; do
	[[ -z "$line" || "$line" == "List of devices attached" ]] && continue

	if [[ "$line" =~ ^(.*[^[:space:]])[[:space:]]+device([[:space:]].*)?$ ]]; then
		serial="${BASH_REMATCH[1]}"
		details="${BASH_REMATCH[2]:-}"
		model=""
		product=""

		if [[ "$details" =~ model:([^[:space:]]+) ]]; then
			model="${BASH_REMATCH[1]}"
		fi
		if [[ "$details" =~ product:([^[:space:]]+) ]]; then
			product="${BASH_REMATCH[1]}"
		fi

		DEVICE_SERIALS+=("$serial")

		label=""
		[[ -n "$model" ]] && label+="model:$model"
		if [[ -n "$product" ]]; then
			[[ -n "$label" ]] && label+="  "
			label+="product:$product"
		fi
		[[ -n "$label" ]] && label+="  "
		label+="$serial"
		DEVICE_LABELS+=("$label")
	fi
done < <(adb devices -l)

if [[ -n "$TARGET" ]]; then
	found=false
	for serial in "${DEVICE_SERIALS[@]}"; do
		if [[ "$serial" == "$TARGET" ]]; then
			found=true
			break
		fi
	done

	if [[ "$found" != true ]]; then
		echo "Target '$TARGET' is not connected and ready." >&2
		echo "Available devices:" >&2
		if [[ ${#DEVICE_SERIALS[@]} -eq 0 ]]; then
			echo "  (none)" >&2
		else
			printf '  %s\n' "${DEVICE_LABELS[@]}" >&2
		fi
		exit 1
	fi
elif [[ ${#DEVICE_SERIALS[@]} -eq 0 ]]; then
	echo "No Android device/emulator connected." >&2
	exit 1
elif [[ ${#DEVICE_SERIALS[@]} -eq 1 ]]; then
	TARGET="${DEVICE_SERIALS[0]}"
else
	echo "Multiple Android devices/emulators connected:"
	PS3="Select target: "
	select label in "${DEVICE_LABELS[@]}"; do
		if [[ -n "$label" ]]; then
			TARGET="${DEVICE_SERIALS[REPLY - 1]}"
			break
		fi
		echo "Invalid selection." >&2
	done
fi

./gradlew assembleDebug

ADB=(adb -s "$TARGET")

"${ADB[@]}" install -r "$APK"
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell am start -n "$PACKAGE/.MainActivity" >/dev/null

echo "Bločky deployed to $TARGET."
