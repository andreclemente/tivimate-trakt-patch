#!/usr/bin/env bash
set -euo pipefail
APK_PATH=${APK_PATH:-${1:-}}
if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "usage: APK_PATH=/path/app.apk $0" >&2
  exit 2
fi
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BASE=$(basename "$APK_PATH" .apk)
mkdir -p "$ROOT/research/apktool" "$ROOT/research/jadx" "$ROOT/research/jadx-single"
apktool d -f -o "$ROOT/research/apktool/$BASE" "$APK_PATH"
if command -v jadx >/dev/null 2>&1; then JADX=jadx; else JADX="$ROOT/tools/bin/jadx/bin/jadx"; fi
if [[ -x "$JADX" ]]; then
  "$JADX" --show-bad-code --deobf -j 1 --no-debug-info -d "$ROOT/research/jadx/$BASE" "$APK_PATH" || {
    echo "WARN: full JADX failed; try targeted --single-class runs" >&2
  }
fi
