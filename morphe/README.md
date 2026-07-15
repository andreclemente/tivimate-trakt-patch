# Morphe bundle

Production Morphe bundle for TiviMate Trakt sync.

## Modules

- `patches/`: settings bootstrap and committed-transaction runtime hook.
- `extensions/trakt/`: native settings UI, encrypted Device Authorization, progress capture, Xtream metadata resolution, and Worker-backed Trakt scrobbling.

No separate shared extension is required; required helper classes are packaged inside `extensions/trakt.mpe`.

## Build

```sh
./gradlew --no-daemon :extensions:trakt:assembleRelease :patches:buildAndroid
```

Output:

```text
patches/build/libs/patches-<version>.mpp
```

Morphe dependencies use GitHub Packages. Configure `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` or `GITHUB_ACTOR`/`GITHUB_TOKEN` in the environment. Never commit them.
