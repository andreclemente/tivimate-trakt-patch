# TiviMate Trakt Sync Patch

Personal-use research and reproducible patching workspace for adding runtime Trakt synchronization to a legitimately installed TiviMate Premium APK.

## Boundaries

This project does **not** remove or bypass TiviMate Premium licensing, DRM, purchase checks, provider access controls, IPTV credentials, or content access mechanisms. APK files, private app data, databases, provider URLs, tokens, signing keys, and patched proprietary APKs must not be committed.

## Product goal

The product goal is **runtime sync with Trakt via hooks/functions**, not a backup-file workflow.

Runtime integration should:

- observe TiviMate playback/progress/watched/unwatched behavior at runtime;
- map movie/episode state into neutral sync events;
- queue and retry Trakt sync safely in the background;
- avoid UI-thread network work;
- avoid logging or transmitting IPTV/provider secrets or stream URLs.

Backup/export analysis is only a research fallback for proving local schema/state behavior.

## Current milestone

Identify, with concrete evidence, where TiviMate stores playback progress and watched status for movies and episodes, then select the smallest stable runtime hook point.

## Workflow

1. Validate local tooling and device/emulator access.
2. Inventory the supplied APK/split APKs.
3. Decompile and index code/resources.
4. Locate and verify storage/playback hooks through static evidence, runtime logs, and controlled state diffs.
5. Build standalone Trakt sync core and queue independent of TiviMate.
6. Prototype runtime hooks with Frida/LSPosed-style instrumentation.
7. Only after hook behavior is proven, design the reproducible patch/module.

## Key docs

- `docs/architecture.md` — runtime-first architecture and non-goals.
- `docs/patch-strategy.md` — reproducible APK patch delivery plan.
- `docs/patch-framework-survey.md` — Morphe patch framework decision and risks.
- `docs/android-device-support.md` — Android TV + phone/tablet patch constraints.
- `docs/testing-now.md` — what can be tested now and exact Morphe commands.
- `morphe/` — Morphe patch bundle scaffold. Morphe is the target framework.
- `docs/trakt-login-settings.md` — Trakt login/settings screen design.
- `docs/trakt-api.md` — Trakt API contract.
- `docs/sync-queue.md` — durable queue/idempotency design.
- `docs/runtime-capture-runbook.md` — what to run on emulator/device.
- `docs/static-wall-and-emulator.md` — why runtime evidence is needed.

## Usage placeholders

Set the APK path before running inspection scripts:

```sh
export APK_PATH=/absolute/path/to/base.apk
./tools/inspect-apk.sh
```

Run static search:

```sh
./tools/search-code.sh 'last_played_positions|episode_last_played_positions|history_programs'
```
