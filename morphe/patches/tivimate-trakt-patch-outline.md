# TiviMate Trakt Morphe Patch Outline

Pseudo-code sketch of the intended Morphe/ReVanced-style patches.

```kotlin
val tivimateTraktSettingsPatch = bytecodePatch(
    name = "TiviMate Trakt settings/login",
    description = "Adds Trakt device-code login and sync settings to TiviMate."
) {
    compatibleWith(tivimateCompatibility)

    execute {
        // TODO after mapping:
        // 1. Add manifest activity for com.tivimate.traktpatch.settings.TraktSettingsActivity.
        // 2. Inject extension classes/resources.
        // 3. Patch settings list/menu to open TraktSettingsActivity.
    }
}

val tivimateTraktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime sync",
    description = "Hooks TiviMate runtime playback/progress/watched events and syncs them to Trakt."
) {
    compatibleWith(tivimateCompatibility)
    dependsOn(tivimateTraktSettingsPatch)

    execute {
        // TODO after runtime evidence:
        // 1. Match player/domain/DB method fingerprint.
        // 2. Insert invoke-static to PlaybackHookBridge.
        // 3. Pass media metadata/progress/watched args.
    }
}
```

Required fingerprints:

```text
TiviMateSettingsMenuFingerprint
TiviMatePlaybackProgressUpdateFingerprint
TiviMateWatchedStateUpdateFingerprint
TiviMateMediaMetadataAccessorFingerprint
```

Do not implement real patches until these are evidence-backed.
