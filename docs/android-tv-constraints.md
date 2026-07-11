# Android TV Constraint

## Current constraint

The user can currently run TiviMate only on an Android TV / TV box, not on a phone.

Therefore the patch must be designed, tested, and documented as **TV-first**.

## Product implications

### UI/UX

- Settings/login UI must be usable with a D-pad remote.
- Every actionable control needs visible focus state.
- No phone-only gestures, small touch targets, or text-entry-heavy flows.
- Device-code OAuth is the preferred login flow because it avoids typing passwords on TV.
- The Trakt authorization screen must show:
  - large readable `user_code`;
  - `https://trakt.tv/activate`;
  - clear `Waiting…`, `Connected`, `Expired`, `Error`, and `Retry` states.
- Buttons must have visible loading/status feedback; silent remote clicks feel broken.

### Android components

The injected settings activity should be compatible with Android TV/Leanback environments.

Patch/manifest considerations:

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

Do **not** make touchscreen required.

The patch should not add a phone-only launcher entry as the normal path. If a temporary launcher/debug alias is needed during development, it must be optional and removable.

### Testing

Primary manual testing is on TV over ADB/network ADB.

Minimum TV validation:

- patched APK installs on Android TV/TV box;
- TiviMate still launches normally with remote;
- Trakt settings can be opened with D-pad only;
- focus moves predictably through all controls;
- connect/cancel/retry/disconnect states are visible;
- playback remains smooth while sync events are queued;
- app restart preserves auth/settings state.

### Runtime capture

Run Frida/ADB captures against the TV device if no phone/emulator can run TiviMate.

Expected flow:

```sh
adb connect <tv-ip>:5555
adb devices -l
frida-ps -U | head
```

Then use the existing capture scripts from `docs/runtime-capture-runbook.md`.

## Patch implementation rule

Any injected Trakt settings screen or debug screen must be considered broken until it is navigable with a TV remote/D-pad.

Community patch scaffolds must carry this constraint in comments/manifests so future patch code does not accidentally become phone-only.
