# TiviMate Trakt Community Patch Bundle

Minimal ReVanced/Morphe-family patch-bundle scaffold for the TiviMate Trakt APK patch.

## Why ReVanced template first?

Morphe is based on the ReVanced patch model, but its current Gradle plugin is distributed through GitHub Packages and was heavier to smoke-test in this container. The upstream ReVanced template builds with the same core model: patch bundle + extension DEX + bytecode/resource patches.

This scaffold keeps the project aligned with community patch ecosystems instead of a bespoke patcher.

## Current patches

- `TiviMate Trakt settings/login` — non-invasive scaffold.
- `TiviMate Trakt runtime sync` — non-invasive scaffold.

Both currently compile but do not modify TiviMate. They are placeholders until the settings and playback hook fingerprints are proven.

## Build prerequisites

The ReVanced Gradle plugin is hosted on GitHub Packages. Set credentials with `read:packages` access:

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

## Smoke test

```sh
cd community-patches
./gradlew --no-daemon :patches:build :extensions:extension:assembleRelease
```

Expected: Gradle builds the patch classes and extension APK/DEX artifacts.

## Next implementation gates

1. Map TiviMate settings menu insertion point.
2. Map runtime playback/progress/watched hook point.
3. Replace placeholder TODOs with evidence-backed fingerprints and bytecode/resource edits.
