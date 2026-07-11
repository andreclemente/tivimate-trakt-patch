# Test plan

## Current milestone

Identify, with concrete evidence, where TiviMate stores playback progress and watched status of movies and episodes.

## Device prerequisites

- Android TV device or emulator with TiviMate installed.
- APK package: `ar.tvplayer.tv`.
- ADB connected.
- Prefer root or `run-as ar.tvplayer.tv` access to `/data/data/ar.tvplayer.tv/databases/`.

## State diff sequence

Use exactly one known movie and one known episode.

```sh
./tools/pull-app-data.sh state-A-never-played
# play movie ~5 minutes, stop
./tools/pull-app-data.sh state-B-movie-partial
./tools/compare-databases.sh research/database-dumps/state-A-never-played research/database-dumps/state-B-movie-partial

# mark same movie watched in UI
./tools/pull-app-data.sh state-C-movie-watched
./tools/compare-databases.sh research/database-dumps/state-B-movie-partial research/database-dumps/state-C-movie-watched

# mark same movie unwatched in UI
./tools/pull-app-data.sh state-D-movie-unwatched
./tools/compare-databases.sh research/database-dumps/state-C-movie-watched research/database-dumps/state-D-movie-unwatched
```

Repeat equivalent states for one episode.

## Success criteria

- Identify exact database file.
- Identify exact table and columns changed by partial movie playback.
- Identify exact table and columns changed by movie watched/unwatched actions.
- Identify exact table and columns changed by partial episode playback.
- Identify exact table and columns changed by episode watched/unwatched actions.
- Verify persistence after app restart.
