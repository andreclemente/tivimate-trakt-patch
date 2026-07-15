# TiviMate Trakt Sync Patch

Morphe patch bundle adding native Trakt Device Authorization and runtime movie/episode progress sync to TiviMate 8K Pro 5.1.6 (`ar.tvplayer.tv`).

## Current release

- Version: `0.1.55`
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
python3 -m unittest -v \
  tests.test_progress_math \
  tests.test_xtream_url_builder \
  tests.test_tv_diagnostic_bundle
node --test oauth-worker/test/worker.test.mjs

cd morphe
./gradlew --no-daemon :extensions:trakt:assembleRelease :patches:buildAndroid
```

Patch verification requires a legally obtained TiviMate 5.1.6 APK and Morphe CLI:

```sh
java -jar morphe-cli.jar patch \
  --exclusive \
  -p=dist/patches-0.1.55.mpp \
  -e='TiviMate Trakt settings/login' \
  -e='TiviMate Trakt runtime progress sync' \
  -o=patched.apk \
  Tivi8KPro.apk
```

See [`docs/architecture.md`](docs/architecture.md) and [`docs/verification.md`](docs/verification.md).
