# Architecture

```text
TiviMate committed DB transaction
  -> TraktProgressBridge (bounded/coalesced background capture)
  -> XtreamMetadataResolver (bounded queue, no redirects)
  -> stable TMDB/IMDb identity + season/episode
  -> TraktSyncClient
  -> authenticated Cloudflare Worker
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

- `<80%`: `/v1/scrobble/pause`
- `>=80%`: `/v1/scrobble/stop`

## Authentication

Trakt Device Authorization and refresh pass through the Worker. Client secret never enters the APK. Access and refresh tokens are encrypted with an Android Keystore-backed AES/GCM key. Expired access tokens are refreshed; `401/403` invalidates local auth; `429/5xx` receives one bounded retry.
