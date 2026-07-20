# TiviMate Trakt Sync Patch

Morphe patch bundle adding native Trakt Device Authorization and runtime movie/episode progress sync to TiviMate 5.1.6 (`ar.tvplayer.tv`).

## Current release

- Version: `1.0.0`
- Manager source: `https://raw.githubusercontent.com/andreclemente/tivimate-trakt-patch/main/patches-bundle.json`
- Target: TiviMate 5.1.6 (`versionCode 5161`)
- Patches:
  - `TiviMate Trakt settings/login`
  - `TiviMate Trakt runtime progress sync`

## Behavior

1. Adds a native, D-pad focusable Trakt entry under **Settings > Other**.
2. Uses Trakt Device Authorization through the project Cloudflare Worker.
3. Watches committed changes in TiviMate's movie and episode progress tables.
4. Resolves stable movie/show identity from Xtream metadata.
5. Sends authenticated Trakt `pause` below 80% and `stop` at or above 80%.
6. Honors separate movie and show sync settings.
7. Caches watched/playback state at startup without scanning the provider catalog.
8. Reconciles native watched state for every stable-ID-confirmed duplicate when movie or show details open.
9. Coalesces repeated detail opens through bounded, single-flight synchronization queues.
10. Prevents imported native writes from echoing back to Trakt while preserving later genuine local updates.
11. Provides confirmed disconnect with remote authorization revocation and local credential cleanup.

No title-only matching is used. Missing stable IDs are skipped.

## Security boundaries

This project does not bypass TiviMate licensing, DRM, purchases, provider controls, or content access. Proprietary APKs, provider URLs, credentials, app data, tokens, signing keys, and patched APKs must never be committed.

- Trakt client secret stays in the Worker.
- TV tokens use Android Keystore-backed encrypted storage.
- Provider requests preserve configured HTTP/HTTPS transport but never follow redirects.
- Provider URLs, credentials, tokens, and raw database rows are not logged.
- Network work runs outside TiviMate database transactions.

## Build and test

```sh
python3 -m unittest discover -s tests -v
node --test oauth-worker/test/worker.test.mjs

cd morphe
./gradlew --no-daemon :extensions:trakt:assembleRelease :patches:buildAndroid
cp patches/build/libs/patches-1.0.0.mpp ../dist/patches-1.0.0.mpp
cd ..
sha256sum -c SHA256SUMS
```

Patch verification requires a legally obtained TiviMate 5.1.6 APK and Morphe CLI:

```sh
java -jar morphe-cli.jar patch \
  --exclusive \
  -p=dist/patches-1.0.0.mpp \
  -e='TiviMate Trakt settings/login' \
  -e='TiviMate Trakt runtime progress sync' \
  -o=patched.apk \
  TiviMate.apk
```

See [`docs/architecture.md`](docs/architecture.md) and [`docs/verification.md`](docs/verification.md).
