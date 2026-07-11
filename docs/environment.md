# Environment validation

Updated after root package install.

## Host
Linux a06c68bf712d 6.12.33-production+truenas #1 SMP PREEMPT_DYNAMIC Wed Dec 17 21:17:21 UTC 2025 x86_64 GNU/Linux
PRETTY_NAME="Debian GNU/Linux 13 (trixie)"
NAME="Debian GNU/Linux"
VERSION_ID="13"
VERSION="13 (trixie)"
VERSION_CODENAME=trixie
DEBIAN_VERSION_FULL=13.4
ID=debian
HOME_URL="https://www.debian.org/"
SUPPORT_URL="https://www.debian.org/support"
BUG_REPORT_URL="https://bugs.debian.org/"

## Toolchain
- `java`: `/usr/bin/java`
- `javac`: `/usr/bin/javac`
- `adb`: `/usr/bin/adb`
- `aapt`: `/usr/bin/aapt`
- `aapt2`: `/usr/bin/aapt2`
- `apksigner`: `/usr/bin/apksigner`
- `zipalign`: `/usr/bin/zipalign`
- `apktool`: `/usr/bin/apktool`
- `smali`: `/usr/bin/smali`
- `baksmali`: `/usr/bin/baksmali`
- `sqlite3`: `/usr/bin/sqlite3`
- `rg`: `/usr/bin/rg`
- `jadx`: `/opt/data/tivimate-trakt-project/tools/bin/jadx/bin/jadx (downloaded local v1.5.6; full decompile killed by OOM)`

## Versions
```text
openjdk version "21.0.11" 2026-04-21
OpenJDK Runtime Environment (build 21.0.11+10-1-deb13u2-Debian)
OpenJDK 64-Bit Server VM (build 21.0.11+10-1-deb13u2-Debian, mixed mode, sharing)
javac 21.0.11
Android Debug Bridge version 1.0.41
Version 34.0.5-debian
Installed as /usr/lib/android-sdk/platform-tools/adb
Android Asset Packaging Tool, v0.2-debian
0.9
Zip alignment utility
Copyright (C) 2009 The Android Open Source Project

2.7.0-dirty
3.46.1 2024-08-13 09:16:08 c9c2ab54ba1f5f46360f1b4f35d849cd3f080e6fc2b6c60e91b16c63f69aalt1 (64-bit)
ripgrep 14.1.1
```

## ADB devices
```text
* daemon not running; starting now at tcp:5037
* daemon started successfully
List of devices attached

```

## Notes
- Apktool decompilation succeeded into `research/apktool/Tivi8KPro/`.
- JADX 1.5.6 was installed under `tools/bin/jadx/`, but full Java decompilation was killed by the container OOM killer at ~64%; use narrower JADX runs or smali/resource analysis until more RAM is available.
