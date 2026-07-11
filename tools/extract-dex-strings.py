#!/usr/bin/env python3
"""Extract visible DEX strings from an APK and filter useful table/schema terms."""
from __future__ import annotations
import re, struct, sys, zipfile
from pathlib import Path

TERMS = [
    'last_played_positions', 'episode_last_played_positions', 'history_programs',
    'movie', 'episode', 'series', 'season', 'duration', 'position', 'played',
    'progress', 'history', 'program', 'playlist', 'category', 'stream', 'tvg',
    'backup', 'restore', 'database', 'CREATE TABLE', 'SQLite',
]

def u4(data: bytes, off: int) -> int:
    return struct.unpack_from('<I', data, off)[0]

def read_uleb(data: bytes, off: int) -> tuple[int, int]:
    res = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        res |= (b & 0x7f) << shift
        if not b & 0x80:
            return res, off
        shift += 7

def dex_strings(data: bytes) -> list[str]:
    if not data.startswith(b'dex\n'):
        return []
    size, off = u4(data, 0x38), u4(data, 0x3c)
    out = []
    for i in range(size):
        so = u4(data, off + 4 * i)
        _, p = read_uleb(data, so)
        q = data.index(b'\0', p)
        out.append(data[p:q].decode('utf-8', 'replace'))
    return out

def main() -> int:
    if len(sys.argv) != 2:
        print('usage: extract-dex-strings.py app.apk', file=sys.stderr)
        return 2
    apk = Path(sys.argv[1])
    all_strings: list[str] = []
    with zipfile.ZipFile(apk) as zf:
        for name in zf.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                strings = dex_strings(zf.read(name))
                all_strings.extend(strings)
                print(f'## {name}: {len(strings)} strings')
    print(f'## total: {len(all_strings)} strings')
    for term in TERMS:
        hits = [s for s in all_strings if term.lower() in s.lower()]
        print(f'\n# {term} ({len(hits)})')
        for s in hits[:200]:
            print(s)
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
