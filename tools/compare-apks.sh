#!/usr/bin/env bash
set -euo pipefail
A="${1:-}"
B="${2:-}"
if [[ -z "${A}" || -z "${B}" || ! -f "${A}" || ! -f "${B}" ]]; then
  echo "Usage: $0 before.apk after.apk" >&2
  exit 2
fi
mkdir -p research/findings/apk-diffs
sha256sum "${A}" "${B}" | tee research/findings/apk-diffs/hashes.txt
unzip -l "${A}" > research/findings/apk-diffs/before.zip-list.txt
unzip -l "${B}" > research/findings/apk-diffs/after.zip-list.txt
diff -u research/findings/apk-diffs/before.zip-list.txt research/findings/apk-diffs/after.zip-list.txt | tee research/findings/apk-diffs/zip-list.diff || true
