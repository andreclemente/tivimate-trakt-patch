# TiviMate Trakt Morphe Patch Bundle

This is the only target patch framework for the project.

## Decision

Use **Morphe**, not ReVanced, for the TiviMate Trakt APK patch.

Expected final patch command shape:

```sh
java -jar morphe-cli.jar patch --patches tivimate-trakt-patches.mpp input/TiviMate.apk
```

## Current status

Scaffold only. The patch stubs compile structurally but do not modify TiviMate until these are mapped:

1. TiviMate settings/menu insertion point.
2. Playback/progress/watched hook point.
3. Media metadata extraction path.

## Planned patches

- `TiviMate Trakt settings/login`
- `TiviMate Trakt runtime sync`
- optional debug/runtime discovery patch

## Device support

The patch must support both Android TV/TV box and phone/tablet:

- TV: D-pad/focus navigation;
- phone/tablet: touch navigation;
- no required Leanback feature;
- no required touchscreen feature.

## Build notes

Morphe Gradle artifacts are resolved from GitHub Packages:

```properties
# ~/.gradle/gradle.properties
gpr.user=<github-user>
gpr.key=<github-token-with-read-packages>
```

or environment variables:

```sh
export GITHUB_ACTOR=<github-user>
export GITHUB_TOKEN=<github-token-with-read-packages>
```

Build smoke test:

```sh
cd morphe
./gradlew --no-daemon :patches:build
```

If building extensions, a full Android SDK is required.
