# Morphe Build Smoke Test

## Result

The Morphe-native patch scaffold builds locally, including a patch bundle artifact.

Commands run:

```sh
cd morphe
GRADLE_USER_HOME=/tmp/tivimate-morphe-gradle-home ./gradlew --no-daemon :patches:compileKotlin :patches:stub:build
GRADLE_USER_HOME=/tmp/tivimate-morphe-gradle-home ./gradlew --no-daemon :patches:buildAndroid :extensions:trakt:assembleRelease
```

Results:

```text
BUILD SUCCESSFUL
3 actionable tasks: 3 executed

BUILD SUCCESSFUL
47 actionable tasks: 45 executed, 2 up-to-date
```

Generated artifacts included:

```text
morphe/patches/build/libs/patches-0.1.0.mpp
morphe/extensions/trakt/build/outputs/apk/release/trakt-release-unsigned.apk
morphe/extensions/trakt/build/intermediates/dex/release/minifyReleaseWithR8/classes.dex
```

Build artifacts are ignored and not committed.

## Notes

- This validates the Morphe Gradle plugin, Kotlin patch stubs, Android extension module, and `.mpp` bundle generation.
- It does **not** modify TiviMate yet.
- Real bytecode/resource changes still require evidence-backed fingerprints:
  - settings/menu insertion point;
  - playback/progress/watched hook point;
  - media metadata extraction path.

## Credentials

Morphe artifacts resolve from GitHub Packages. Local builds require credentials with package-read access via:

```properties
gpr.user=<github-user>
gpr.key=<github-token>
```

or `GITHUB_ACTOR` / `GITHUB_TOKEN`.

## Android SDK

Building the extension requires a full Android SDK. This local smoke test used a temporary SDK path:

```text
/tmp/tivimate-android-sdk
```

The local `morphe/local.properties` file points to that SDK and is ignored by git.
