# Android Device Support

## Correct scope

The patch must support **both**:

- Android TV / TV box, because that is the user's current available test/runtime device;
- Android phone/tablet, because the patched APK should not become TV-only.

The current testing constraint is **TV available now**, not **TV-only product**.

## Product rule

The Trakt patch must be form-factor neutral:

- usable with D-pad/remote on TV;
- usable with touch on phone/tablet;
- no manifest feature should require only TV or only touchscreen;
- no UI flow should rely exclusively on TV remote keys or phone touch gestures.

## Login UX

Device-code OAuth remains preferred because it works well on TV and phone:

```text
Go to https://trakt.tv/activate
Code: ABCD1234
```

Phone/tablet may additionally support clickable/copyable URL/code later, but TV must not require copy/paste.

## Manifest requirements

Do not make touchscreen mandatory:

```xml
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

Do not make Leanback mandatory either:

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
```

These entries mean compatible with both device classes, not TV-only.

## UI requirements

Settings/login screen must have:

- visible focus state for D-pad;
- normal touch targets for phone/tablet;
- large readable code/URL on TV;
- portrait/landscape tolerant layout;
- no hover-only/touch-only/remote-only controls;
- visible loading/status feedback after actions.

## Testing matrix

### Current mandatory test path

Android TV / TV box via network ADB:

```sh
adb connect <tv-ip>:5555
adb devices -l
```

### Future/when available

Phone/tablet or emulator:

```sh
adb devices -l
```

Validate both:

- app launches;
- Trakt settings opens;
- device-code login state displays;
- queue/sync continues while playing;
- auth/settings persist after restart.

## Implementation rule

Any injected Trakt settings screen or debug screen is incomplete until it works with both:

1. TV D-pad/remote navigation;
2. phone/tablet touch navigation.
