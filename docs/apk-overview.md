# APK overview

Status: Initial APK inventory completed using `curl`, `sha256sum`, Python `zipfile`, `strings`, and `androguard` because Android SDK/JADX/Apktool are not installed in this container.

## Input artifact

- Source URL: `https://nextcloud.quemsou.eu/s/QDr24gaDR79sc9c/download`
- Downloaded file: `input/Tivi8KPro.apk`
- Content-Type from server: `application/vnd.android.package-archive`
- Content-Length from server: `58299932`
- Content-Disposition filename: `Tivi8KPro.apk`
- Last-Modified from server: `Mon, 25 May 2026 13:10:05 GMT`

## Hashes

Evidence files:
- `research/hashes/Tivi8KPro.apk.sha256`
- `research/hashes/Tivi8KPro.apk.sha1`
- `research/hashes/Tivi8KPro.apk.md5`

```text
SHA-256 2e0357529e221781ca73982d38573b602c0af1f665cbcb1c3fe8828687b2c2e3
SHA-1   fe20ad3a71041e068117e30851a64f9a816e46b7
MD5     becaa43e95357f496bebcceb1a4169d5
```

## APK identity

Evidence: `research/manifests/Tivi8KPro.androguard-summary.txt`

```text
package: ar.tvplayer.tv
version_name: 5.1.6
version_code: 5161
min_sdk: 21
target_sdk: 33
main_activity: com.andyhax.haxsplash.LaunchActivity
```

## Signing

Evidence: `research/manifests/Tivi8KPro.androguard-summary.txt`

```text
is_signed_v1: True
is_signed_v2: True
is_signed_v3: True
cert_0_sha256: a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc
```

`androguard` version used here does not expose `is_signed_v4()`, so v4 status is unknown until `apksigner` is available.

## Archive inventory

Evidence: `research/manifests/Tivi8KPro.zip-inventory.txt`

```text
APK size: 58299932 bytes
File count: 1387
DEX count: 2
classes.dex  9580188 bytes
classes2.dex 774404 bytes
Native library count: 44
Architectures: arm64-v8a, armeabi-v7a, x86, x86_64
```

Native libraries include, for every ABI:

```text
libHaxBHook.so
libavutil.so
libbytehook.so
libdexprotector.so
libdpboot.so
libffmpegJNI.so
libhax.so
liborigin.so
librtmp-jni.so
libshadowhook.so
libshadowhook_nothing.so
```

## Protection / packing evidence

Confirmed evidence:
- Manifest main activity is `com.andyhax.haxsplash.LaunchActivity`.
- Manifest contains `ar.tvplayer.tv.ProtectedTvPlayerApplication$ProtectedTvPlayerApplication$G`.
- Native libraries include `libdexprotector.so`, `libdpboot.so`, `libhax.so`, `libHaxBHook.so`, `libbytehook.so`, and `libshadowhook.so`.
- `strings` found `libdexprotector.so` references.

Conclusion: this APK is protected/packed. Static decompilation may be incomplete; runtime instrumentation and state comparison will likely be required.

## Framework/library evidence

Evidence: `research/findings/dex-class-search.txt`, `research/findings/initial-string-hit-counts.txt`, `research/manifests/Tivi8KPro.androguard-summary.txt`

Confirmed:
- Kotlin evidence: Kotlin constructor marker and Kotlin-style data model methods in DEX, e.g. `Lkotlin/jvm/internal/DefaultConstructorMarker;` in constructors.
- Room evidence:
  - Manifest service: `androidx.room.MultiInstanceInvalidationService`
  - Classes: `Lar/tvplayer/core/data/db/TvPlayerDatabase;`, `Lar/tvplayer/core/data/db/TvPlayerDatabase_Impl;`, `Lar/tvplayer/core/data/db/TvgProgramsDatabase;`, `Lar/tvplayer/core/data/db/TvgProgramsDatabase_Impl;`
- Android Media3 / ExoPlayer evidence:
  - Classes: `Landroidx/media3/exoplayer/ExoPlayer;`, `Landroidx/media3/common/PlaybackException;`, many `androidx/media3/exoplayer/*` classes.
- WorkManager evidence:
  - Manifest services/receivers include `androidx.work.impl.*`.
  - Classes include `Landroidx/work/impl/WorkDatabase;` and worker classes under `ar.tvplayer.core.data.repositories` / `ar.tvplayer.core.domain2`.
- OkHttp evidence:
  - Class search/strings found OkHttp package references, including `Lokhttp3/internal/publicsuffix/PublicSuffixDatabase;`.
- Moshi evidence:
  - Many generated `JsonAdapter` classes for API/data models.

Not confirmed from current static evidence:
- Retrofit
- Gson as primary serializer
- Dagger/Hilt
- RxJava
- Jetpack Compose
- Android View Binding / Data Binding

## Decompilation status

Blocked in this container:
- `jadx: command not found`
- `apktool: command not found`
- `java: command not found`

Partial replacement performed:
- Used `androguard` via `uv run --with androguard` to decode manifest and enumerate DEX classes/methods/fields.

