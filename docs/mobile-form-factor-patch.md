# Mobile / tablet form-factor patch

## Status

**Planned, but not implemented until its native gate is captured.** This is a separate optional Morphe patch from Trakt settings and Trakt sync.

The target is not a GrapheneOS workaround. The unmodified official app also fails on the phone, so the trigger is the non-TV form factor.

## Confirmed static evidence — 8K 5.1.6 sample

- The manifest already makes both `android.hardware.touchscreen` and `android.software.leanback` optional.
- `com.andyhax.haxsplash.LaunchActivity` has both `LAUNCHER` and `LEANBACK_LAUNCHER` categories.
- The manifest requests `android:reqNavigation="dpad"` and locks the launch activity to landscape.
- The launch activity's `attachBaseContext` and `onCreate` are native methods.
- The APK uses a protected bootstrap (`libdexprotector.so`, `libdpboot.so`, `libhax.so` and related native bootstrap code).

Therefore a manifest-only patch is not a credible fix: the phone rejection can be in native bootstrap code or decrypted code that is unavailable to static JADX/Apktool analysis. The static `UiModeManager.getCurrentModeType() == 4` helper found in the APK is from Media3 utility code and has no static call site in the visible DEX, so it is not a safe gate target.

## Existing runtime evidence — do not request it again

The user already supplied several Pixel 9a / GrapheneOS Android 17 crash reports and a Morphe log. They establish three separate facts:

- Rebuilt/re-signed 8K 5.1.6 reaches `MainActivity` then fails to inflate protected `res/2pus8l29.xml` with `Corrupt XML binary file`.
- A separate rebuilt 5.1.5 route fails earlier at `androidx.startup.InitializationProvider` with `NoClassDefFoundError`.
- The official 5.3.3 APK can fail inside Morphe/apkzlib before patch execution (`ExtraField$AlignmentSegment`).

These reports prove that the protected 8K APK cannot currently survive generic rebuild/re-signing. They are **not evidence that a Trakt hook caused the crash** and must not be requested again.

The only still-unobserved datum is the first failure from the **untouched original app on the phone**. It is useful for choosing the mobile form-factor bypass, but it is not a blocker for continuing static/protected-bootstrap work now. When a PC/ADB connection is available, `tools/capture-mobile-launch.sh` can capture it in one launch attempt; redact playlist URLs, provider credentials and tokens before sharing it.

## Implementation path after evidence

1. Fingerprint the actual form-factor decision in the protected/decrypted runtime, using the captured stack and a narrow runtime trace if needed.
2. Add an optional `TiviMate mobile/tablet form factor` Morphe patch for only the proven APK versions.
3. Bypass only the launch/device-class rejection. Do **not** alter premium/licensing, billing, provider access, or account logic.
4. Preserve TV behavior: on Android TV the patch must be a no-op.
5. Validate phone launch, touch navigation, back behavior, portrait/landscape handling, and TV D-pad navigation separately.

## Acceptance criteria

A release of this patch is valid only if all are true:

- the original APK fails on the target phone and the same-version patched APK reaches `MainActivity`;
- it does not regress launch on Android TV;
- basic touch controls work without an external mouse/remote;
- it has visible UI feedback rather than a silent failure;
- no licensing/provider-authentication behavior was modified.

A successful install alone is not evidence of success.
