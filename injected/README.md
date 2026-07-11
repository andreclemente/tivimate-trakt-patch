# Injected Runtime Components

This directory describes the classes/assets that the APK patcher will inject into the patched APK.

Planned Java package:

```text
com.tivimate.traktpatch
```

Planned components:

```text
settings/TraktSettingsActivity
settings/TraktSettingsStore
auth/TraktDeviceAuthManager
sync/TraktClient
sync/TraktSyncQueue
sync/TraktSyncWorker
hooks/PlaybackHookBridge
```

Implementation will start as Java/Kotlin source or smali templates after patch points are mapped.

No proprietary TiviMate code belongs here.
