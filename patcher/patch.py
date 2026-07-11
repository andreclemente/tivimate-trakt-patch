#!/usr/bin/env python3
"""Reproducible TiviMate Trakt APK patcher skeleton.

This is intentionally non-functional until APK fingerprints, settings patch points,
and runtime hook points are proven.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass
class ApkInfo:
    path: str
    sha256: str
    size: int


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def inspect_apk(path: Path) -> ApkInfo:
    return ApkInfo(path=str(path), sha256=sha256_file(path), size=path.stat().st_size)


def main() -> int:
    parser = argparse.ArgumentParser(description="Patch TiviMate APK with Trakt runtime sync")
    parser.add_argument("--input", required=True, type=Path, help="User-supplied TiviMate APK")
    parser.add_argument("--output", required=True, type=Path, help="Patched APK output path")
    parser.add_argument("--experimental", action="store_true", help="Allow unsupported fingerprints during research")
    parser.add_argument("--report", type=Path, default=Path("output/patch-report.json"))
    args = parser.parse_args()

    if not args.input.exists():
        parser.error(f"input APK not found: {args.input}")

    info = inspect_apk(args.input)
    report = {
        "status": "blocked",
        "reason": "patch points not implemented yet",
        "input": asdict(info),
        "output": str(args.output),
        "experimental": args.experimental,
        "next_steps": [
            "map settings UI patch point",
            "map runtime playback/progress hook point",
            "add manifest/resource/smali injection steps",
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
