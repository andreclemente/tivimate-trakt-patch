# APK Patch Framework Survey

## Why this exists

The project should not reinvent a full APK patch ecosystem if an existing community patch framework can do the boring parts:

- decode/rebuild/sign APKs;
- package patches;
- apply bytecode/resource/manifest edits;
- support extension/injected code;
- expose CLI/manager UX;
- manage patch options and version compatibility.

The goal remains a **TiviMate APK patch** that adds Trakt runtime sync. The framework is just delivery infrastructure.

## Surveyed ecosystem

### Morphe / ReVanced-family patchers

Examples:

- `MorpheApp/morphe-manager`
- `MorpheApp/morphe-cli`
- `MorpheApp/morphe-patches`
- ReVanced patcher ecosystem
- third-party community patch bundles built on the same model

Observed capabilities from Morphe docs/repos:

- CLI patch command:

```sh
java -jar morphe-cli.jar patch --patches patches.mpp input.apk
```

- Multiple patch bundles are supported:

```sh
java -jar morphe-cli.jar patch --patches patches-a.mpp --patches patches-b.mpp input.apk
```

- Patch options are supported via `-O` and JSON options files.
- Patch types include bytecode patches and resource/manifest patches.
- Patches can inject extension code and call it from modified bytecode.
- Existing patches use fingerprints rather than only fixed obfuscated names.

This aligns well with our needs:

```text
TiviMate APK
  ↓ Morphe/ReVanced-family patch bundle
Injected Trakt settings/auth/sync code
  ↓ bytecode/resource/manifest patches
Patched APK
```

## Recommendation

Use a **Morphe/ReVanced-family patch bundle** as the preferred patch infrastructure, instead of growing a bespoke patcher.

Keep `patcher/patch.py` only as a tiny research/fallback harness until the Morphe-compatible patch module exists.

## Proposed repo layout

```text
morphe/
├── README.md
├── patches/
│   └── src/main/kotlin/.../tivimate/trakt/
│       ├── TraktSettingsPatch.kt
│       ├── TraktRuntimeSyncPatch.kt
│       └── Fingerprints.kt
└── extensions/
    └── trakt/
        └── src/main/.../com/tivimate/traktpatch/
            ├── settings/TraktSettingsActivity
            ├── auth/TraktDeviceAuthManager
            ├── sync/TraktClient
            ├── sync/TraktSyncQueue
            └── hooks/PlaybackHookBridge
```

Actual Gradle structure should follow the chosen framework's current template after a build smoke test.

## Patch decomposition

### Patch 1 — Trakt settings/login

Purpose:

- Inject `TraktSettingsActivity`.
- Add manifest entry.
- Add row/entry point inside TiviMate settings.
- Add device OAuth UI and token storage.

Needs mapping:

- TiviMate settings list class/method/resource.

### Patch 2 — Runtime sync hook

Purpose:

- Hook the proven playback/progress/watched method(s).
- Emit neutral `sync-core` events.
- Enqueue events.

Needs mapping:

- player/domain/DB hook method;
- media metadata fields;
- watched/progress semantics.

### Patch 3 — Debug/runtime discovery, optional

Purpose:

- Add temporary logs or debug UI to validate patched APK without Frida.
- Must be disabled by default and not log secrets.

## Risks / checks before committing fully

1. **License:** Morphe patches are GPLv3 with additional section 7 terms. If we derive from Morphe code/templates or publish a Morphe patch bundle, repo licensing must be compatible.
2. **Protected APK:** TiviMate uses protection/packing. Framework bytecode patches may still work on visible/injected parts, but hidden runtime-loaded classes remain a challenge.
3. **Settings integration:** Adding a clean settings row still requires mapping TiviMate's settings UI.
4. **Networking dependencies:** Injected Trakt client should avoid pulling huge dependencies. Prefer Java/Kotlin stdlib + platform HTTP APIs if possible.
5. **Version compatibility:** Need robust fingerprints and explicit supported TiviMate version/hash.
6. **Patch bundle build:** Need a local Morphe/ReVanced patch bundle smoke test before deleting custom patcher fallback.

## Immediate next steps

1. Add a `morphe/` scaffold in this repo.
2. Create patch stubs:
   - `TraktSettingsPatch`
   - `TraktRuntimeSyncPatch`
   - `Fingerprints`
3. Ensure patch UI remains cross-device: TV/D-pad and phone/tablet touch.
4. Keep stubs non-invasive until runtime evidence maps exact methods.
5. Run a patch bundle build smoke test.
6. Use TV/network-ADB logs first, then phone/tablet logs when available, to fill fingerprints and injection points.
