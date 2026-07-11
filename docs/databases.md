# Database/storage findings

Status: Initial static storage table names identified from Room generated database code. Playback progress/watched storage is still not proven by state diff.

## Confirmed Room database classes

Evidence:
- `research/jadx-single/ar.tvplayer.core.data.db.TvPlayerDatabase_Impl.java`
- `research/apktool/Tivi8KPro/smali/ar/tvplayer/core/data/db/TvPlayerDatabase_Impl.smali`
- Manifest service: `androidx.room.MultiInstanceInvalidationService`

Confirmed classes:

```text
ar.tvplayer.core.data.db.TvPlayerDatabase
ar.tvplayer.core.data.db.TvPlayerDatabase_Impl
ar.tvplayer.core.data.db.TvgProgramsDatabase
ar.tvplayer.core.data.db.TvgProgramsDatabase_Impl
```

`TvPlayerDatabase_Impl` exposes 21 DAO accessors and lists the main Room tables.

## Main database table list

Evidence: `TvPlayerDatabase_Impl.java:244-249`

```text
playlists
channels
channel_groups
channel_group_links
channels_fts
tvg_sources
tvg_channels
channel_group_options
channel_manual_positions
channel_tvg_bindings
playlist_tvg_source_assignments
last_played_positions
recordings
reminders
history_programs
my_programs
movie_categories
movies
movies_fts
series_categories
series
series_fts
episode_last_played_positions
search_queries
dummy
```

FTS external-content mapping evidence:

```text
channels_fts -> channels
movies_fts -> movies
series_fts -> series
```

## Playback/history storage candidates

High-value candidates from table names:

```text
last_played_positions
episode_last_played_positions
history_programs
my_programs
movies
series
```

Current interpretation:
- `last_played_positions` probably stores generic/channel/movie last played positions or VOD movie resume positions.
- `episode_last_played_positions` probably stores series episode resume positions.
- `history_programs` probably stores linear/EPG history, not necessarily VOD watched state.
- `my_programs` may store user-selected programs/favorites, not yet related to watched state.

These are hypotheses until verified with database dumps or runtime SQL logs.

## Static-analysis limitation

Searches found the table list, but not `CREATE TABLE` SQL for these app tables, nor obvious SQL queries referencing `last_played_positions` outside `TvPlayerDatabase_Impl`. The APK uses DexProtector/protection assets (`classes.dex.dat`, `libdexprotector.so`), so some schema/DAO code may be protected, transformed, or only fully available at runtime.

## Required next proof

Use `tools/pull-app-data.sh` on a device where `run-as ar.tvplayer.tv` works, or on a rooted device via equivalent root pull, then compare before/after controlled playback actions with `tools/compare-databases.sh`.
