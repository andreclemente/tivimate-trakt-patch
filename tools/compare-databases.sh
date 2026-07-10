#!/usr/bin/env bash
set -euo pipefail
A="${1:-}"
B="${2:-}"
if [[ -z "${A}" || -z "${B}" || ! -f "${A}" || ! -f "${B}" ]]; then
  echo "Usage: $0 before.db after.db" >&2
  exit 2
fi
if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "ERROR: sqlite3 not found" >&2
  exit 127
fi
mkdir -p research/findings/database-diffs
sqlite3 "${A}" '.schema' > research/findings/database-diffs/before.schema.sql
sqlite3 "${B}" '.schema' > research/findings/database-diffs/after.schema.sql
sqlite3 "${A}" '.dump' > research/findings/database-diffs/before.dump.sql
sqlite3 "${B}" '.dump' > research/findings/database-diffs/after.dump.sql
diff -u research/findings/database-diffs/before.schema.sql research/findings/database-diffs/after.schema.sql | tee research/findings/database-diffs/schema.diff || true
diff -u research/findings/database-diffs/before.dump.sql research/findings/database-diffs/after.dump.sql | tee research/findings/database-diffs/dump.diff || true
