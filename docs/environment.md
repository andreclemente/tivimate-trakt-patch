# Environment validation

Generated from live diagnostics on 2026-07-10.

## Host

```text
Linux a06c68bf712d 6.12.33-production+truenas #1 SMP PREEMPT_DYNAMIC Wed Dec 17 21:17:21 UTC 2025 x86_64 GNU/Linux
Debian GNU/Linux 13 (trixie), VERSION_ID=13, DEBIAN_VERSION_FULL=13.4
```

## Tool availability

| Tool | Status | Evidence |
|---|---:|---|
| Java runtime (`java`) | Missing | `java: command not found` |
| JDK (`javac`) | Missing | `javac: command not found` |
| Android SDK env | Missing | no `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `JAVA_HOME` in environment |
| ADB | Missing | `adb: command not found` |
| aapt/aapt2 | Missing | `aapt: command not found`, `aapt2: command not found` |
| apkanalyzer | Missing | not on `PATH` |
| apksigner | Missing | `apksigner: command not found` |
| zipalign | Missing | `zipalign: command not found` |
| JADX | Missing | `jadx: command not found` |
| Apktool | Missing | `apktool: command not found` |
| smali/baksmali | Missing | not on `PATH` |
| Frida CLI/tools | Missing | `frida: command not found` |
| Python | Present | `Python 3.13.5` |
| Git | Present | `git version 2.47.3` |
| unzip | Present | on PATH |
| strings | Present | `/usr/bin/strings` |
| sqlite3 CLI | Missing | `sqlite3: command not found` |

## Android device/emulator

No device status could be checked because `adb` is not installed in this container.

Unknown until ADB is available and a test Android TV device/emulator is connected:

- Android TV CPU architecture
- Android version
- Root status
- Magisk availability
- LSPosed availability
- USB/network ADB connectivity

## APK discovery

Searches under `/opt/data` found no `*.apk`, `*.apks`, or `*.xapk` files.

## Phase 0 conclusion

Confirmed:
- Repository can be created locally.
- Python and Git are available.

Blocked:
- APK inventory, signature inspection, manifest decoding, decompilation, and device/runtime inspection cannot proceed until Android reverse-engineering tools and a TiviMate APK/split APK set are supplied.
