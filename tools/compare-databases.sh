#!/usr/bin/env bash
set -euo pipefail
BEFORE=${1:?before dump dir}
AFTER=${2:?after dump dir}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/research/findings/state-diff-$(basename "$BEFORE")-vs-$(basename "$AFTER").txt"
{
  echo "# Database state diff"
  echo "Before: $BEFORE"
  echo "After: $AFTER"
  echo
  diff -ruN "$BEFORE" "$AFTER" || true
} > "$OUT"
echo "$OUT"
