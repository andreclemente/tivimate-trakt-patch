# APK Patch Strategy

## Decision

Delivery target is a **reproducible APK patch**. LSPosed/Frida are research/prototyping tools only, not the final product path.

The patcher must take a user-supplied legitimate TiviMate APK, validate supported fingerprints, inject Trakt runtime sync components, rebuild/sign locally, and never commit or distribute patched proprietary APKs.

## Patch goals

Preferred infrastructure is a Morphe patch bundle if build smoke tests confirm it fits TiviMate. The custom `patcher/` stays as fallback/research harness.

1. Add Trakt login/settings entry inside TiviMate.
2. Add runtime hooks/functions for playback progress and watched/unwatched state.
3. Queue Trakt sync events in app-private storage.
4. Sync in background without blocking playback/UI.
5. Preserve TiviMate licensing/provider behavior untouched.

## High-level patch flow

```text
input/TiviMate.apk
  ↓ validate hash/version/fingerprints
apktool decode
  ↓ inject smali/resources/classes
patch settings UI entry
patch/hook playback or DB update path
patch AndroidManifest if new components are required
  ↓ rebuild
zipalign
sign with user-provided/debug key
verify installability
output/patched.apk
```

## Injection components

Planned injected package namespace:

```text
com.tivimate.traktpatch
```

Components:

```text
com.tivimate.traktpatch.settings.TraktSettingsActivity
com.tivimate.traktpatch.auth.TraktDeviceAuthManager
com.tivimate.traktpatch.sync.TraktSyncQueue
com.tivimate.traktpatch.sync.TraktSyncWorker
com.tivimate.traktpatch.sync.TraktClient
com.tivimate.traktpatch.hooks.PlaybackHookBridge
```

## Settings integration

Preferred approach:

1. Add a `Trakt` row under TiviMate Settings.
2. Row opens injected `TraktSettingsActivity`.
3. Activity shows:
   - connection status;
   - button: `Connect Trakt`;
   - device-code user code + verification URL;
   - button: `Disconnect`;
   - sync toggles for movies/episodes/progress/watched.

If patching an existing settings list is too fragile, fallback is:

- expose an injected standalone activity launched via Android settings intent or hidden launcher alias during development;
- later wire into the native settings menu once UI class is mapped.

## Auth flow

Use Trakt Device OAuth.

1. User clicks `Connect Trakt`.
2. Patched app calls `/oauth/device/code`.
3. UI displays `user_code` and `verification_url`.
4. Background polling calls `/oauth/device/token` at Trakt-provided interval.
5. Tokens stored in app-private encrypted preferences if available, otherwise app-private SharedPreferences with explicit security note.
6. Refresh token via `/oauth/token` before expiry.

## Runtime hook strategy

Final hook point is not selected yet. Candidate order:

1. Domain/repository method that updates progress/watched state.
2. Player callback method with media metadata available.
3. Narrow SQLite table write hook for confirmed tables.

Avoid final broad SQLite interception if a narrower domain/player method is proven.

## Safety requirements

- Do not alter TiviMate purchase/licensing code.
- Do not alter provider authentication/access code.
- Do not log full stream URLs.
- Do not send provider credentials to Trakt.
- Do not perform network calls from UI/player callback/hooked DB method directly.
- Do not commit APKs, patched APKs, private DBs, tokens, signing keys.

## Version support

The patcher must refuse unsupported APKs unless `--experimental` is passed.

Fingerprint inputs:

- package name: `ar.tvplayer.tv`;
- version name/code;
- SHA-256;
- presence of known classes/resources;
- stable bytecode/resource fingerprints for settings and hook points.

## Current blocker

Exact settings UI class and playback/watched hook point still require runtime/static mapping. Frida scripts in `tools/frida/` are for discovering those points on emulator.
