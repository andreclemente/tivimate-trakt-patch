# Morphe Phone Smoke Test Result

## Result

Phone test with Morphe Manager succeeded through the patching pipeline but the output APK did not launch.

Observed Morphe patch log:

```text
pkg=ar.tvplayer.tv version=5.1.6 bundle=0.1.2 input=ar.tvplayer.tv_5.1.6_original.apk split=false patches=2
TiviMate Trakt runtime sync succeeded
TiviMate Trakt settings/login succeeded
Patched apk saved ... output.apk
Patching succeeded
```

The scaffold patches are no-op placeholders, so this failure happens before any real Trakt logic exists.

## Local APK evidence

The inspected TiviMate 5.1.6 APK contains DexProtector-style protection assets/libraries:

```text
assets/classes.dex.dat
assets/ic.dat
assets/resources.dat
assets/se.dat
lib/*/libdexprotector.so
lib/*/libdpboot.so
```

This strongly suggests the app may not survive a generic Morphe rebuild/re-sign cycle, even when the patch code itself is no-op.

## Current hypothesis

Root cause is probably one of:

1. DexProtector / anti-tamper detects APK rebuild or signature change;
2. Morphe/apktool changes compression/alignment/resource metadata required by the protector;
3. protected bootstrap code cannot load encrypted payload after rebuild;
4. Android 17 tightens behavior that this protected/rebuilt APK hits at startup.

## What is needed next

Need app crash log after launching the patched APK, not only Morphe patch log.

Look for:

```text
AndroidRuntime
FATAL EXCEPTION
ar.tvplayer.tv
libdexprotector
libdpboot
UnsatisfiedLinkError
ClassNotFoundException
VerifyError
SecurityException
```

Without that launch crash, do not keep iterating patch metadata.

## Product implication

The Morphe path may still be viable, but only if we first solve protected-APK rebuild/signing/bootstrap compatibility. Runtime Trakt hooks cannot be validated until patched TiviMate can launch unchanged/no-op.
