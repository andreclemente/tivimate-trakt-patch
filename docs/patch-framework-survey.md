# APK Patch Framework Survey

## Decision

Use **Morphe** for the TiviMate Trakt APK patch.

Do **not** target ReVanced. ReVanced was briefly used only as a local scaffold smoke-test because its template was easy to build, but the user explicitly wants Morphe. The working project direction is now Morphe-only.

## Why Morphe

Morphe provides the community patch infrastructure we want:

- patch bundles (`.mpp`);
- Morphe CLI / Manager workflow;
- bytecode/resource/manifest patch APIs;
- extension/injected runtime code;
- patch options;
- package/version compatibility metadata;
- fingerprints instead of brittle fixed obfuscated names.

Expected final command shape:

```sh
java -jar morphe-cli.jar patch --patches tivimate-trakt-patches.mpp input/TiviMate.apk
```

## Repo layout

```text
morphe/
├── settings.gradle.kts
├── patches/
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/tivimate/traktpatch/patches/
│       ├── TiviMateCompatibility.kt
│       ├── TraktSettingsPatch.kt
│       ├── TraktRuntimeSyncPatch.kt
│       └── Fingerprints.kt
└── extensions/
    └── trakt/
        └── src/main/java/com/tivimate/traktpatch/extension/
            └── TraktPatchExtension.java
```

## Patch decomposition

### Patch 1 — Trakt settings/login

Purpose:

- Inject/enable Trakt settings runtime code.
- Add row/entry point inside TiviMate settings.
- Add device OAuth UI and token storage.
- Work on Android TV and phone/tablet.

Needs mapping:

- TiviMate settings list class/method/resource.

### Patch 2 — Runtime sync hook

Purpose:

- Hook proven playback/progress/watched method(s).
- Emit neutral `sync-core` events.
- Enqueue events.

Needs mapping:

- player/domain/DB hook method;
- media metadata fields;
- watched/progress semantics.

### Patch 3 — Debug/runtime discovery, optional

Purpose:

- Add temporary logs or debug UI to validate patched APK without Frida.
- Must be disabled by default and must not log secrets.

## Risks / checks

1. **Morphe build access:** Morphe Gradle artifacts are in GitHub Packages; local builds require package-read credentials.
2. **License:** Morphe uses GPLv3 with additional section 7 terms. Morphe-derived code/templates must keep compatible licensing and notices.
3. **Protected APK:** TiviMate uses protection/packing. Visible bytecode/resource patches may work, but hidden runtime-loaded classes still need runtime evidence.
4. **Settings integration:** Adding a clean settings row requires mapping TiviMate's settings UI.
5. **Networking dependencies:** Injected Trakt client should avoid heavy dependencies; prefer platform APIs.
6. **Version compatibility:** Use robust fingerprints and explicit supported TiviMate version/hash.

## Immediate next steps

1. Build the Morphe scaffold locally when credentials/SDK allow.
2. Use TV/network-ADB logs first to fill fingerprints.
3. Implement `TraktSettingsPatch` only after settings insertion point is known.
4. Implement `TraktRuntimeSyncPatch` only after playback/progress/watched hooks are known.
