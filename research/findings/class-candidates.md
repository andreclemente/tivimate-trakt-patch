# Candidate classes

## Main Room database

Original class: `ar.tvplayer.core.data.db.TvPlayerDatabase`
Original method: 21 abstract DAO accessors, e.g. `ᴵʼ()Lˈᐧ/ˎˉ;`, `יʻ()Lˈᐧ/ﾞˊ;`, etc.
Proposed responsibility: Main Room database for TiviMate local state/catalog.
Reason for suspicion: Class name, Room base class inheritance, generated `_Impl` class, manifest Room invalidation service.
References:
- `research/jadx-single/ar.tvplayer.core.data.db.TvPlayerDatabase.java`
- `research/jadx-single/ar.tvplayer.core.data.db.TvPlayerDatabase_Impl.java`
Evidence:
- `_Impl.mo1381()` returns Room table list containing playlist/channel/movie/series/history/position tables.
Confidence: High for main database; low for exact watched/progress method mapping.
Next test: Pull runtime database and inspect `.schema`; map DAO accessors to table operations.

## Generated main database implementation

Original class: `ar.tvplayer.core.data.db.TvPlayerDatabase_Impl`
Original method: `mo1381()` / smali `ٴˎ()`
Proposed responsibility: Room generated implementation and table registry.
Reason for suspicion: Lists all main Room tables and DAO factory lazy fields.
References:
- `research/jadx-single/ar.tvplayer.core.data.db.TvPlayerDatabase_Impl.java:244-249`
- `research/apktool/Tivi8KPro/smali/ar/tvplayer/core/data/db/TvPlayerDatabase_Impl.smali:681-770`
Evidence:
- Contains table names `last_played_positions`, `episode_last_played_positions`, `history_programs`, `movies`, `series`.
Confidence: High.
Next test: Runtime schema dump and state diffs.

## Movie/VOD progress table candidate

Original class: unknown DAO/repository.
Original method: unknown.
Proposed responsibility: Read/write movie or generic last played positions.
Reason for suspicion: Table name `last_played_positions` in main Room database.
References:
- `TvPlayerDatabase_Impl.java:249`
Evidence:
- Table exists in Room registry; user-visible strings include `Reset positions` and `Highlight progress only`.
Confidence: Medium for progress relevance; low for movie-specific meaning.
Next test: Compare DB before/after partial movie playback and manual watched/unwatched actions.

## Episode progress table candidate

Original class: unknown DAO/repository.
Original method: unknown.
Proposed responsibility: Read/write episode last played/resume positions.
Reason for suspicion: Table name `episode_last_played_positions` in main Room database.
References:
- `TvPlayerDatabase_Impl.java:249`
Evidence:
- Explicit `episode_` prefix and `last_played_positions` wording.
Confidence: Medium-high for episode progress relevance; low for watched/completed semantics.
Next test: Compare DB before/after partial episode playback and completion.

## Playback UI component

Original class: `ar.tvplayer.tv.player.ui.CustomPlayerView`
Original method: unknown.
Proposed responsibility: Player UI surface around playback controls/state.
Reason for suspicion: Smali references from synthetic class `ـᐧ/יʻ.smali`; Media3/ExoPlayer classes present.
References:
- `research/apktool/Tivi8KPro/smali_classes2/ـᐧ/יʻ.smali`
Evidence:
- Field typed as `Lar/tvplayer/tv/player/ui/CustomPlayerView;`.
Confidence: Low until callback methods are mapped.
Next test: Targeted JADX/smali search for Media3 player listener and hooks around `CustomPlayerView`.
