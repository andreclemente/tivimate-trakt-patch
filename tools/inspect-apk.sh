#!/usr/bin/env bash
set -euo pipefail

APK_PATH="${APK_PATH:-${1:-}}"
OUT_DIR="${OUT_DIR:-research}"

if [[ -z "${APK_PATH}" ]]; then
  echo "ERROR: set APK_PATH=/absolute/path/to/base.apk or pass APK path as first argument" >&2
  exit 2
fi
if [[ ! -f "${APK_PATH}" ]]; then
  echo "ERROR: APK not found: ${APK_PATH}" >&2
  exit 2
fi

mkdir -p "${OUT_DIR}/hashes" "${OUT_DIR}/manifests" "${OUT_DIR}/strings"
base_name="$(basename "${APK_PATH}")"
stem="${base_name%.*}"

sha256sum "${APK_PATH}" | tee "${OUT_DIR}/hashes/${base_name}.sha256"

if command -v apksigner >/dev/null 2>&1; then
  apksigner verify --verbose --print-certs "${APK_PATH}" | tee "${OUT_DIR}/manifests/${stem}.apksigner.txt"
else
  echo "WARN: apksigner not found" | tee "${OUT_DIR}/manifests/${stem}.apksigner.txt"
fi

if command -v aapt >/dev/null 2>&1; then
  aapt dump badging "${APK_PATH}" | tee "${OUT_DIR}/manifests/${stem}.aapt-badging.txt"
  aapt dump permissions "${APK_PATH}" | tee "${OUT_DIR}/manifests/${stem}.aapt-permissions.txt"
  aapt dump xmltree "${APK_PATH}" AndroidManifest.xml | tee "${OUT_DIR}/manifests/${stem}.manifest-xmltree.txt"
elif command -v aapt2 >/dev/null 2>&1; then
  aapt2 dump badging "${APK_PATH}" | tee "${OUT_DIR}/manifests/${stem}.aapt2-badging.txt"
else
  echo "WARN: aapt/aapt2 not found" | tee "${OUT_DIR}/manifests/${stem}.aapt.txt"
fi

unzip -l "${APK_PATH}" | tee "${OUT_DIR}/manifests/${stem}.zip-list.txt"
strings "${APK_PATH}" > "${OUT_DIR}/strings/${stem}.strings.txt"

python3 - <<'PY' "${APK_PATH}" "${OUT_DIR}/manifests/${stem}.dex-libs.txt"
from pathlib import Path
import sys, zipfile
apk=Path(sys.argv[1]); out=Path(sys.argv[2])
with zipfile.ZipFile(apk) as z:
    names=z.namelist()
    dex=[n for n in names if n.startswith('classes') and n.endswith('.dex')]
    libs=[n for n in names if n.startswith('lib/') and n.endswith('.so')]
    lines=[]
    lines.append(f'APK: {apk}')
    lines.append(f'DEX count: {len(dex)}')
    for n in dex:
        info=z.getinfo(n); lines.append(f'  {n}\t{info.file_size} bytes')
    arches=sorted({n.split('/')[1] for n in libs if len(n.split('/'))>=3})
    lines.append(f'Native library count: {len(libs)}')
    lines.append('Architectures: ' + (', '.join(arches) if arches else 'none detected'))
    for n in libs:
        info=z.getinfo(n); lines.append(f'  {n}\t{info.file_size} bytes')
    out.write_text('\n'.join(lines)+'\n')
    print(out.read_text())
PY
