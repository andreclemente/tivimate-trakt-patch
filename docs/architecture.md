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

## Runtime strategy options

### Research prototype: Frida

Use first for discovery. Fast to iterate and easy to disable.

Pros:
- No APK rebuild.
- Good method/SQL/player callback tracing.

Cons:
- Not final user-friendly delivery.
- Needs rooted/debuggable/emulator or Frida server.

### Personal runtime module: LSPosed

Likely best second step after hook points are proven.

Pros:
- Clean separation from proprietary APK.
- Runtime hooks without distributing modified TiviMate APK.

Cons:
- Requires rooted/LSPosed environment.

### Reproducible APK patch

Only after hook points are stable.

Pros:
- Self-contained patched APK for personal use.

Cons:
- More fragile with protected APK.
- Rebuild/sign/install friction.
- Must not distribute proprietary patched APK.

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
