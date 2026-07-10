#!/usr/bin/env bash
set -euo pipefail
QUERY="${1:-}"
if [[ -z "${QUERY}" ]]; then
  echo "Usage: $0 <regex>" >&2
  exit 2
fi
if command -v rg >/dev/null 2>&1; then
  rg -n --hidden --glob '!*.apk' --glob '!*.dex' --glob '!*.so' "${QUERY}" research/jadx research/apktool research/strings docs || true
else
  grep -RInE "${QUERY}" research/jadx research/apktool research/strings docs || true
fi
