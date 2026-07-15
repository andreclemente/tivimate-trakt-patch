# Architecture

The product is bidirectional. A release is incomplete unless both directions are runtime-proven:

```text
TiviMate -> Trakt
Trakt -> TiviMate
```

## Outbound path

```text
TiviMate committed DB transaction
  -> TraktProgressBridge (bounded/coalesced background capture)
  -> XtreamMetadataResolver (bounded queue, no redirects)
  -> stable TMDB/IMDb identity + season/episode
  -> TraktSyncClient
  -> Trakt API directly (Bearer token + public client ID)
  -> Trakt scrobble pause/stop
```

## Runtime hook

Morphe injects `TraktProgressBridge.onTransactionEnded(SQLiteDatabase)` after TiviMate commits `TvPlayer.db` transactions. Startup reads a baseline before processing changes. Database capture, metadata requests, and Trakt writes run outside the transaction thread.

Tracked state:

- `movies`: `playlist_id`, `xc_id`, position, duration.
- `episode_last_played_positions`: series/episode provider IDs, position, duration.

## Identity

Xtream playlist wrappers are parsed without decoding and re-encoding credential values incorrectly. Metadata requests retain configured HTTP/HTTPS transport and disable redirects. Movies require stable TMDB or IMDb IDs. Episodes require season, episode number, and a stable show TMDB or IMDb ID.

## Progress semantics

Playback position and duration are milliseconds. Progress is clamped to `0..100`:

- `<80%`: `/scrobble/pause`
- `>=80%`: `/scrobble/stop`

## Inbound contract

```text
Trakt watched movies + watched shows + active playback
  -> Trakt API directly (Bearer token + public client ID)
  -> stable TMDB/IMDb identity matching
  -> bounded import plan
  -> parameterized writes to TvPlayer.db
  -> native TiviMate watched/resume state
```

Required behavior:

- A movie marked watched on Trakt appears watched in TiviMate even if never played locally.
- An episode marked watched on Trakt appears watched in TiviMate even if never played locally.
- Active Trakt movie/episode progress imports when TiviMate has an authoritative duration.
- Completed state dominates partial state; otherwise merge monotonically with the greater progress.
- Imported writes are suppressed from outbound echo.
- Final matches require TMDB/IMDb identity; title/year may shortlist candidates but never establish identity.
- Unsupported or ambiguous provider items are skipped rather than guessed.
- Network and provider metadata work never runs on TiviMate's transaction/UI thread.

Runtime acceptance requires visible native watched-state proof for one Trakt-only movie and one Trakt-only episode, persistence after restart, partial-resume proof, and proof that a later genuine local update still exports.

## Authentication

Only Trakt Device Authorization, token refresh, and public-client-ID bootstrap pass through the Worker. All watched/playback reads and scrobble writes go directly from Android to `https://api.trakt.tv`. The client secret never enters the APK; the public client ID is stored alongside tokens inside the Android Keystore-backed AES/GCM encrypted JSON. Existing installs fetch `/v1/client` once when that field is absent. Expired access tokens are refreshed through the Worker; `401/403` invalidates local auth; `429/5xx` receives one bounded retry.
