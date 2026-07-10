#!/usr/bin/env bash
set -euo pipefail
APK_PATH="${APK_PATH:-${1:-}}"
if [[ -z "${APK_PATH}" || ! -f "${APK_PATH}" ]]; then
  echo "ERROR: set APK_PATH or pass an APK path" >&2
  exit 2
fi
mkdir -p research/jadx research/apktool
if command -v jadx >/dev/null 2>&1; then
  jadx --show-bad-code --deobf -d research/jadx "${APK_PATH}"
else
  echo "ERROR: jadx not found" >&2
  exit 127
fi
if command -v apktool >/dev/null 2>&1; then
  apktool d -f -o research/apktool "${APK_PATH}"
else
  echo "ERROR: apktool not found" >&2
  exit 127
fi
