#!/usr/bin/env bash
set -euo pipefail
PKG=${PKG:-ar.tvplayer.tv}
LABEL=${1:-$(date -u +%Y%m%dT%H%M%SZ)}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/research/database-dumps/$LABEL"
mkdir -p "$OUT"
echo "Output: $OUT"
adb devices -l
adb shell "run-as $PKG ls -la /data/data/$PKG/databases" | tee "$OUT/databases.ls.txt"
for f in $(adb shell "run-as $PKG sh -c 'cd /data/data/$PKG/databases 2>/dev/null && ls -1'" | tr -d '\r'); do
  echo "Pulling $f"
  adb exec-out "run-as $PKG cat /data/data/$PKG/databases/$f" > "$OUT/$f" || true
done
find "$OUT" -maxdepth 1 -type f -name '*.db' -o -name '*.sqlite' -o -name '*database*' | while read -r db; do
  sqlite3 "$db" '.tables' > "$db.tables.txt" 2>/dev/null || true
  sqlite3 "$db" '.schema' > "$db.schema.sql" 2>/dev/null || true
done
