# TiviMate Trakt Morphe Patch Outline

Morphe-native patch outline. ReVanced is not a target.

Required Morphe patch stubs:

```text
TraktSettingsPatch.kt
TraktRuntimeSyncPatch.kt
Fingerprints.kt
```

Required runtime extension namespace:

```text
com.tivimate.traktpatch.extension
```

Do not implement bytecode mutations until fingerprints are evidence-backed.
