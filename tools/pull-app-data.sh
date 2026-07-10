#!/usr/bin/env bash
set -euo pipefail
PKG="${PKG:-ar.tvplayer.tv}"
DEST="${DEST:-research/database-dumps/$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "${DEST}"
if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb not found" >&2
  exit 127
fi
adb devices -l
adb shell run-as "${PKG}" sh -c 'pwd; find . -maxdepth 4 -type f 2>/dev/null' | tee "${DEST}/file-list.txt"
# Pulling app-private data usually requires run-as support/debuggable app or root. Keep this explicit and non-destructive.
echo "If root is available, pull only controlled test data and redact secrets before sharing." > "${DEST}/README.txt"
