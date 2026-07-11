# Runtime hooks

Status: Runtime discovery scripts are prepared. They still need emulator/device execution.

## Scripts

- `tools/frida/dump-sqlite-schema.js` — dumps private SQLite schemas from inside the app process.
- `tools/frida/sqlite-log.js` — logs SQLite reads/writes with redaction and target-table stack traces.
- `tools/frida/player-callback-log.js` — traces common Media3/ExoPlayer/player UI methods.
- `tools/frida/method-trace-template.js` — editable method tracing scaffold once candidate classes are known.

## Target evidence

Focus on:

```text
last_played_positions
episode_last_played_positions
history_programs
movies
series
ar.tvplayer.tv.player.ui.CustomPlayerView
androidx.media3.* player classes
```

## Run examples

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/dump-sqlite-schema.js --no-pause | tee schema.log
frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause | tee sqlite.log
frida -U -f ar.tvplayer.tv -l tools/frida/player-callback-log.js --no-pause | tee player.log
```

See `docs/runtime-capture-runbook.md` for exact user-side steps.

## Privacy

Scripts attempt to redact obvious URLs/tokens/passwords, but logs must still be reviewed before sharing.
