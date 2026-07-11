# Static wall and emulator/runtime path

## What static analysis proved

The visible APK contains:

- Manifest package: `ar.tvplayer.tv`.
- Backup/restore activities:
  - `ar.tvplayer.tv.settings.ui.general.SelectBackupActivity`
  - `ar.tvplayer.tv.settings.ui.general.RestoreBackupActivity`
- Main Room DB classes:
  - `ar.tvplayer.core.data.db.TvPlayerDatabase`
  - `ar.tvplayer.core.data.db.TvPlayerDatabase_Impl`
  - `ar.tvplayer.core.data.db.TvgProgramsDatabase`
  - `ar.tvplayer.core.data.db.TvgProgramsDatabase_Impl`
- Main Room table registry includes:
  - `last_played_positions`
  - `episode_last_played_positions`
  - `history_programs`
  - `movies`
  - `series`

Evidence:

- `AndroidManifest.xml` lines 63-73: backup/restore activities.
- `TvPlayerDatabase_Impl.mo1381()` / smali `ٴˎ()`: table registry.
- DEX string table contains candidate table names but no app `CREATE TABLE` SQL for those tables.

## Static wall

The APK is protected. Evidence:

- Application class in manifest is `com.andyhax.hook.Launch`.
- Protected real app class is `ar.tvplayer.tv.ProtectedTvPlayerApplication`.
- Assets include encrypted-looking payloads:
  - `assets/classes.dex.dat`
  - `assets/resources.dat`
  - `assets/se.dat`
  - `assets/ic.dat`
- `TvPlayerDatabase_Impl` references helper/factory classes that are type references in `classes.dex` but **not class definitions** in visible DEX:
  - `Lʽ/ˏᴵ;`
  - `Lʽ/ᴵʿ;`
  - `Lʼˈ/ـﹶ;`
- These missing classes are likely in the protected runtime payload. They likely contain schema validation and DAO implementation code needed to recover exact columns statically.

Therefore exact watched/progress columns are not recoverable from visible static code alone with current tools.

## Runtime path without TV

Use an emulator on the user's PC/host. Hermes Docker does not need USB or emulator passthrough if logs/files can be copied back.

Minimum runtime goal:

1. Install APK in emulator.
2. Start app once.
3. Run Frida schema dump:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/dump-sqlite-schema.js --no-pause | tee schema.log
```

Expected output markers:

```text
@@DB_SCHEMA@@ {...}
@@DB_TABLE_INFO@@ {...}
```

Send `schema.log` back. This should reveal exact columns for:

```text
last_played_positions
episode_last_played_positions
history_programs
movies
series
```

If Frida is not available, use ADB shell on rooted emulator or `run-as` if debuggable (probably not debuggable):

```sh
adb shell ls -la /data/data/ar.tvplayer.tv/databases
adb pull /data/data/ar.tvplayer.tv/databases ./tivimate-databases
```

## State test after schema

Once schema is known:

1. Capture schema/baseline DB.
2. Play one movie for ~2 minutes.
3. Capture DB.
4. Mark watched/unwatched if possible.
5. Repeat for one episode.

Diff targets:

```text
last_played_positions
episode_last_played_positions
history_programs
movies
series
```
