#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TERM=${1:-}
if [[ -z "$TERM" ]]; then
  echo "usage: $0 <regex>" >&2
  exit 2
fi
rg -n -i --glob '!*.png' --glob '!*.webp' --glob '!*.jpg' --glob '!*.so' "$TERM" \
  "$ROOT/research/apktool" "$ROOT/research/jadx" "$ROOT/research/jadx-single" "$ROOT/research/strings" 2>/dev/null || true
