# APK Patcher Fallback

This directory contains a small research/fallback patcher harness.

## Current decision

Preferred infrastructure is now a **Morphe patch bundle**, documented under `morphe/` and `docs/patch-framework-survey.md`.

Keep this custom patcher only for:

- APK fingerprint inspection;
- patch report generation;
- fallback experiments if the community patch framework cannot handle TiviMate's protected APK.

## Planned CLI fallback

```sh
python3 patcher/patch.py \
  --input input/TiviMate.apk \
  --output output/TiviMate-Trakt-patched.apk \
  --experimental
```

## Current status

Skeleton only. It intentionally exits as blocked until patch points are mapped.
