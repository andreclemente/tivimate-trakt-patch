# Runtime Capture Runbook

## Purpose

Capture enough runtime evidence from an Android emulator/device to choose stable hooks for Trakt sync.

Hermes Docker does not need USB/emulator access. Run these commands on the PC/host that has the emulator/device, then send the logs back.

## Required files from repo

Copy these scripts to the machine that can reach the emulator/device, or run from a clone of this repo:

```text
tools/frida/dump-sqlite-schema.js
tools/frida/sqlite-log.js
tools/frida/player-callback-log.js
tools/frida/method-trace-template.js
```

## Device setup

Current constraint: TiviMate can currently be tested from the user's Android TV / TV box. The patch must still support phone/tablet; this runbook focuses on the available TV path.

1. Enable Developer options + ADB debugging on the TV.
2. Connect from the PC:

```sh
adb connect <tv-ip>:5555
```

3. Install TiviMate APK on the TV.
4. Confirm ADB sees it:

```sh
adb devices -l
```

Expected:

```text
<device-id> device ...
```

4. Install/start Frida server appropriate for the emulator ABI, or use another Frida setup that makes `frida -U` work.

Verify:

```sh
frida-ps -U | head
```

## Capture 1 — database schema

Run:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/dump-sqlite-schema.js --no-pause | tee schema.log
```

Let TiviMate start and sit for 20-30 seconds. Then stop with Ctrl-C.

Expected markers:

```text
@@DB_SCHEMA@@
@@DB_TABLE_INFO@@
```

Send back:

```text
schema.log
```

## Capture 2 — baseline startup SQL

Run:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause | tee sqlite-startup.log
```

Let app open to main screen. Stop with Ctrl-C.

Send back:

```text
sqlite-startup.log
```

## Capture 3 — movie playback

Run:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/sqlite-log.js --no-pause | tee sqlite-movie-playback.log
```

Actions:

1. Note timestamp in terminal.
2. Open one movie/VOD item.
3. Play ~2 minutes.
4. Pause.
5. Resume ~30 seconds.
6. Stop/back out.
7. If UI supports it, mark watched then unwatched.
8. Stop Frida with Ctrl-C.

Send back:

```text
sqlite-movie-playback.log
short note: which movie title/year, when each action happened
```

## Capture 4 — episode playback

Same as movie, but use one episode.

Send back:

```text
sqlite-episode-playback.log
short note: show title, season, episode, when each action happened
```

## Capture 5 — player callbacks

If SQLite logs are unclear, run:

```sh
frida -U -f ar.tvplayer.tv -l tools/frida/player-callback-log.js --no-pause | tee player-callbacks.log
```

Repeat a short movie or episode playback.

Send back:

```text
player-callbacks.log
```

## Privacy check before sending logs

Do not send logs if they contain provider credentials, usernames, passwords, or full stream URLs. The scripts try to redact obvious secrets, but check quickly first.

Useful grep:

```sh
grep -Ei 'password|token|username|authorization|http://|https://' *.log
```

If sensitive lines appear, redact them before sending.

## What Hermes will do with logs

1. Extract table schemas for target tables.
2. Identify which table/method changes for progress and watched state.
3. Narrow hooks to stable app/domain/player methods.
4. Design the first runtime Trakt sync prototype.
