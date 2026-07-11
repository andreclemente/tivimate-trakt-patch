# Build Smoke Test

## Result

The minimal community patch-bundle scaffold builds locally.

Command run:

```sh
cd community-patches
GRADLE_USER_HOME=/tmp/tivimate-gradle-user-home ./gradlew --no-daemon :patches:build :extensions:extension:assembleRelease
```

Result:

```text
BUILD SUCCESSFUL
48 actionable tasks: 26 executed, 2 from cache, 20 up-to-date
```

Generated artifacts included:

```text
patches/build/libs/patches-1.0.4.rvp
extensions/extension/build/outputs/apk/release/extension-release-unsigned.apk
```

Build artifacts are ignored and not committed.

## Local environment notes

The ReVanced patch template/plugin requires:

- GitHub Packages credentials (`gpr.user` / `gpr.key` or equivalent env vars) with `read:packages`;
- Android SDK platform/build-tools.

This container did not have a complete Android SDK. For the smoke test, a temporary SDK was created under `/tmp/tivimate-android-sdk` with:

- Android SDK Platform 33;
- Android SDK Platform 35;
- Android SDK Build-Tools 34.

`community-patches/local.properties` was created locally with `sdk.dir=/tmp/tivimate-android-sdk`; it is ignored by git.

## Important

The current patches are compile-only scaffolds. They do **not** modify TiviMate yet. Real mutation waits for evidence-backed fingerprints:

- settings menu insertion point;
- playback/progress/watched hook point;
- media metadata extraction path.
