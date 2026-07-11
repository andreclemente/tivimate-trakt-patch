# History/progress findings

Status: Table candidates found; watched/progress storage not yet proven.

## Static evidence

Evidence:
- `research/jadx-single/ar.tvplayer.core.data.db.TvPlayerDatabase_Impl.java:244-249`
- `research/apktool/Tivi8KPro/smali/ar/tvplayer/core/data/db/TvPlayerDatabase_Impl.smali:712-760`
- `research/apktool/Tivi8KPro/res/values/strings.xml`

TiviMate includes user-visible history/progress strings:

```text
Clear history?
No history
Show search history
Highlight progress only
Delay before adding to history, sec
History / Recent channels
Reset positions
```

Room table candidates include:

```text
last_played_positions
episode_last_played_positions
history_programs
```

## Current hypotheses

- Movie/VOD resume progress may be in `last_played_positions`.
- Episode resume progress may be in `episode_last_played_positions`.
- Watched/completed state may be represented in those tables, in `movies`/`series`, or derived from position/duration threshold.
- `history_programs` may be for live/EPG history and must not be assumed relevant to VOD watched state.

## Not proven

Unknown:
- Exact database filename.
- Table schemas/columns.
- Primary keys and foreign-key relationships.
- Whether movie watched status has an explicit boolean/timestamp or is derived from progress.
- Whether episode watched status has an explicit boolean/timestamp or is derived from progress.
- Which DAO/repository methods read/write these tables.

## Controlled proof plan

1. Pull baseline DB/app data: `./tools/pull-app-data.sh state-A-never-played`.
2. Play one known movie for ~5 minutes, stop; pull: `./tools/pull-app-data.sh state-B-movie-partial`.
3. Mark same movie watched in UI; pull: `./tools/pull-app-data.sh state-C-movie-watched`.
4. Mark unwatched; pull: `./tools/pull-app-data.sh state-D-movie-unwatched`.
5. Repeat for one episode.
6. Run `./tools/compare-databases.sh <before> <after>` between each pair.
7. Verify changed rows survive TiviMate restart.

Milestone remains open until the changed table/column values are demonstrated.
