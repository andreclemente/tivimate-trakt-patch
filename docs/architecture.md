# Runtime Trakt Sync Architecture

## Goal

Add personal-use Trakt synchronization to TiviMate by observing runtime playback/state behavior and syncing movie/episode progress and watched state to Trakt.

This is **not** a backup-system integration. Backup/export files can help research schema/state, but the final sync path must run from runtime hooks/functions.

## Components

```text
TiviMate runtime
  ↓
Hook layer
  ↓
Event mapper
  ↓
Durable outbox / queue
  ↓
Trakt client
  ↓
Trakt API
```

### Hook layer

Responsibilities:

- Observe playback start/pause/stop/completion events.
- Observe progress/resume-position writes if those are the most stable signal.
- Observe watched/unwatched actions if TiviMate exposes explicit actions.
- Capture only media identity and playback state required for Trakt.

Non-responsibilities:

- No Trakt network calls on UI/player callback threads.
- No stream URL/provider token capture.
- No license/purchase/provider bypass.

### Event mapper

Responsibilities:

- Convert raw hook observations into `sync-core/events.schema.json` events.
- Normalize milliseconds/percentages.
- Attach enough metadata for Trakt matching:
  - movie: title, year, provider item id if available;
  - episode: show title, season, episode, title/year if available.
- Generate stable dedupe IDs.

### Durable outbox / queue

Responsibilities:

- Persist events until Trakt accepts them.
- Collapse duplicates/noisy progress updates.
- Retry transient failures with backoff.
- Preserve watched/unwatched ordering.

### Trakt client

Responsibilities:

- Manage device OAuth tokens.
- Call Trakt scrobble/history endpoints.
- Handle rate limits and auth refresh/retry behavior.
- Never store client secrets or tokens in committed files.

## Runtime strategy

### Final delivery: reproducible APK patch

The chosen final delivery is an APK patch. Frida remains a research tool for discovering hook points; LSPosed is not a target delivery path.

The patcher must:

- validate input version/hash/fingerprints;
- inject Trakt settings/auth/sync components;
- add a Trakt settings entry inside TiviMate;
- hook proven playback/progress/watched functions;
- rebuild/sign locally with a user-owned key;
- refuse unsupported APKs unless explicit experimental mode is used.

See `docs/patch-strategy.md` and `docs/trakt-login-settings.md`.

## Non-goals

- Backup-file based recurring sync.
- Syncing provider/channel credentials.
- Screen scraping or accessibility polling as final design.
- Bypassing TiviMate Premium, purchases, DRM, or provider access controls.
- Generic IPTV stream tracking outside user-owned playback state.

## Current known hook candidates

Static table candidates:

```text
last_played_positions
episode_last_played_positions
history_programs
movies
series
```

Runtime class candidate:

```text
ar.tvplayer.tv.player.ui.CustomPlayerView
```

Potential framework hooks:

```text
android.database.sqlite.SQLiteDatabase
androidx.media3.common.Player$Listener
androidx.media3.exoplayer.ExoPlayer
```

## Evidence gates

Do not implement final Trakt sync until these are proven:

1. Which event(s) fire for movie start/pause/stop/completion.
2. Which event(s) fire for episode start/pause/stop/completion.
3. Which table/method stores resume progress.
4. Whether watched/unwatched is explicit or derived from progress/duration.
5. Which metadata fields can identify movie/episode robustly.
6. Duplicate/noise frequency under normal playback.
