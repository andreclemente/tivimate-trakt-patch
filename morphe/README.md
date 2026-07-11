# Morphe/ReVanced-family Patch Bundle Scaffold

This directory is reserved for a Morphe/ReVanced-family patch bundle for TiviMate Trakt sync.

## Direction

Preferred delivery infrastructure: community patch framework bundle, not a bespoke APK patcher.

The final patched APK should be produced by a standard patch command such as:

```sh
java -jar morphe-cli.jar patch --patches tivimate-trakt-patches.mpp input/TiviMate.apk
```

Exact command/package format depends on the framework template we adopt after a build smoke test.

## Planned patches

- `TiviMate Trakt settings/login`
  - inject Trakt settings screen;
  - add manifest entry;
  - add TiviMate settings row;
  - implement device-code OAuth.

- `TiviMate Trakt runtime sync`
  - hook proven playback/progress/watched functions;
  - emit neutral sync events;
  - enqueue background Trakt sync.

- `TiviMate Trakt debug hooks` optional
  - development-only logging and diagnostics.

## Why not implement here yet?

Patch fingerprints and insertion points are not proven yet. Need emulator/runtime evidence for:

- settings menu class/method/resource;
- playback/progress/watched hook point;
- metadata fields for movie/episode matching.

Until then, this scaffold documents direction without adding brittle fake patches.
