#!/usr/bin/env bash
# Capture the first TiviMate launch failure on a phone/tablet.
# Usage: ./tools/capture-mobile-launch.sh [adb-serial] [output-file]
set -euo pipefail

PACKAGE="ar.tvplayer.tv"
ACTIVITY="com.andyhax.haxsplash.LaunchActivity"
SERIAL="${1:-}"
OUTPUT="${2:-research/runtime-logs/mobile-launch-$(date +%Y%m%d-%H%M%S).log}"

ADB=(adb)
if [[ -n "$SERIAL" ]]; then
  ADB+=( -s "$SERIAL" )
fi

command -v adb >/dev/null || {
  echo "adb is required. Enable Developer options + USB debugging, then install Android platform-tools." >&2
  exit 127
}

mkdir -p "$(dirname "$OUTPUT")"
"${ADB[@]}" wait-for-device

if ! "${ADB[@]}" shell pm path "$PACKAGE" >/dev/null 2>&1; then
  echo "$PACKAGE is not installed on the selected device." >&2
  exit 1
fi

"${ADB[@]}" logcat -b all -c
"${ADB[@]}" shell am force-stop "$PACKAGE"
"${ADB[@]}" shell am start -W -n "$PACKAGE/$ACTIVITY" >"$OUTPUT"
sleep 5

{
  printf '\n===== logcat (first launch attempt) =====\n'
  "${ADB[@]}" logcat -b all -d -v threadtime
} >>"$OUTPUT"

printf 'Saved %s\n' "$OUTPUT"
printf 'Return this file; redact any playlist URLs, provider credentials, or tokens before sharing.\n'
