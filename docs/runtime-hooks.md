# Runtime hooks

Status: Initial research hook created; not executed because no ADB device is attached.

## Scripts

- `tools/frida/sqlite-log.js` — broad SQLite logger for research only.

Purpose:
- Log SQLite `execSQL`, `rawQuery`, insert/update/delete calls.
- Include timestamp and stack trace.
- Avoid logging SQL containing obvious credential/token/auth/url keywords.

Run example:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause
```

Use only during controlled tests. If it is too broad/noisy, narrow to tables confirmed by DB diff, especially:

```text
last_played_positions
episode_last_played_positions
history_programs
movies
series
```
