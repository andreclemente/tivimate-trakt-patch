# TiviMate Trakt Sync Patch

Personal-use research and reproducible patching workspace for adding Trakt synchronization to a legitimately installed TiviMate Premium APK.

## Boundaries

This project does **not** remove or bypass TiviMate Premium licensing, DRM, purchase checks, provider access controls, IPTV credentials, or content access mechanisms. APK files, private app data, databases, provider URLs, tokens, signing keys, and patched proprietary APKs must not be committed.

## Current milestone

Identify, with concrete evidence, where TiviMate stores playback progress and watched status for movies and episodes.

## Workflow

1. Validate local tooling and device access.
2. Inventory the supplied APK/split APKs.
3. Decompile and index code/resources.
4. Locate and verify storage for playback progress and watched status through static evidence, state diffs, and runtime logs.
5. Only after local behavior is proven, design the patch and Trakt integration.

## Usage placeholders

Set the APK path before running inspection scripts:

```sh
export APK_PATH=/absolute/path/to/base.apk
./tools/inspect-apk.sh
```
