# Database/storage findings

Status: Initial static storage candidates identified. Playback progress/watched storage not yet proven.

## Confirmed database classes

Evidence:
- `research/findings/dex-class-search.txt`
- `research/findings/candidate-methods-fields.txt`
- Manifest service `androidx.room.MultiInstanceInvalidationService`

Confirmed Room database candidates:

```text
Lar/tvplayer/core/data/db/TvPlayerDatabase;
Lar/tvplayer/core/data/db/TvPlayerDatabase_Impl;
Lar/tvplayer/core/data/db/TvgProgramsDatabase;
Lar/tvplayer/core/data/db/TvgProgramsDatabase_Impl;
```

`TvPlayerDatabase` exposes 21 DAO-like accessor methods. `TvPlayerDatabase_Impl` contains 21 cached DAO fields. This strongly indicates the main app database, but the actual schema/table names are not recovered yet because JADX/Apktool/Java are unavailable and this APK appears protected.

## Not yet confirmed

Unknown:
- Database filenames.
- Database locations.
- Tables.
- Columns.
- Indexes.
- Foreign keys.
- Triggers.
- Room entities for watched/progress state.
- DAO method responsible for watched/progress state.

## Important blocker

String search found no `CREATE TABLE` / obvious SQL schema strings in the raw APK strings output. Combined with `libdexprotector.so` and protected launch activity, this suggests schema/method bodies may be obfuscated, encrypted, or otherwise hard to recover statically from raw strings.

## Next evidence required

1. Install Java + JADX + Apktool and decompile.
2. Dump `/data/data/ar.tvplayer.tv/databases/` from a controlled test device.
3. Compare database state before/after playback and manual watched/unwatched actions.
4. Use Frida hooks for Room/SQLite if schema or DAO mapping remains unclear.
