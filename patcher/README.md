# APK Patcher

This directory will contain the reproducible APK patcher.

## Goal

Patch a user-supplied legitimate TiviMate APK locally to add Trakt runtime sync.

## Non-goals

- Do not distribute patched APKs.
- Do not include proprietary APK contents in git.
- Do not bypass TiviMate Premium/licensing/provider access.

## Planned CLI

```sh
python3 patcher/patch.py \
  --input input/TiviMate.apk \
  --output output/TiviMate-Trakt-patched.apk \
  --keystore /path/to/debug-or-user.keystore \
  --ks-pass env:KEYSTORE_PASS
```

## Planned phases

1. Validate input APK.
2. Decode with apktool.
3. Apply resource/smali/manifest patches.
4. Inject `com.tivimate.traktpatch` runtime classes.
5. Rebuild.
6. Zipalign.
7. Sign.
8. Verify.
9. Emit patch report.

## Current status

Skeleton only. Exact settings row and hook patch points still need runtime/static mapping.
