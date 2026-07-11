# Trakt API Contract

## Status

Endpoint paths were checked against Trakt's public API host. Authenticated endpoints return `403` without API headers/auth, which is expected for unauthenticated probes. Device OAuth endpoints are POST-only and cannot be meaningfully GET-probed.

Authoritative docs: <https://trakt.docs.apiary.io/>

## Credentials

Required app credentials:

```text
TRAKT_CLIENT_ID
TRAKT_CLIENT_SECRET
```

Do not commit credentials, OAuth tokens, or user-specific auth files.

## Common headers

```http
Content-Type: application/json
trakt-api-version: 2
trakt-api-key: <TRAKT_CLIENT_ID>
Authorization: Bearer <ACCESS_TOKEN>
```

The `Authorization` header is omitted only for initial device code/token steps as documented by Trakt.

## Device OAuth flow

### Request device/user code

```http
POST https://api.trakt.tv/oauth/device/code
Content-Type: application/json

{
  "client_id": "<TRAKT_CLIENT_ID>"
}
```

Expected response fields:

```text
device_code
user_code
verification_url
expires_in
interval
```

### Poll for token

```http
POST https://api.trakt.tv/oauth/device/token
Content-Type: application/json

{
  "code": "<device_code>",
  "client_id": "<TRAKT_CLIENT_ID>",
  "client_secret": "<TRAKT_CLIENT_SECRET>"
}
```

Expected response fields:

```text
access_token
refresh_token
expires_in
created_at
scope
token_type
```

### Refresh token

```http
POST https://api.trakt.tv/oauth/token
Content-Type: application/json

{
  "refresh_token": "<refresh_token>",
  "client_id": "<TRAKT_CLIENT_ID>",
  "client_secret": "<TRAKT_CLIENT_SECRET>",
  "redirect_uri": "urn:ietf:wg:oauth:2.0:oob",
  "grant_type": "refresh_token"
}
```

## Scrobble endpoints

Use for playback progress. Trakt scrobble progress is normally `0.0` to `100.0`.

### Start playback

```http
POST https://api.trakt.tv/scrobble/start
```

### Pause playback

```http
POST https://api.trakt.tv/scrobble/pause
```

### Stop playback

```http
POST https://api.trakt.tv/scrobble/stop
```

Example movie payload:

```json
{
  "movie": {
    "title": "Example Movie",
    "year": 2024,
    "ids": {}
  },
  "progress": 42.5
}
```

Example episode payload:

```json
{
  "show": {
    "title": "Example Show",
    "year": 2024,
    "ids": {}
  },
  "episode": {
    "season": 1,
    "number": 2
  },
  "progress": 42.5
}
```

## History endpoints

Use for explicit watched/unwatched state after a hook proves an explicit completed/watched action.

### Add to watched history

```http
POST https://api.trakt.tv/sync/history
```

### Remove from watched history

```http
POST https://api.trakt.tv/sync/history/remove
```

Example movie history payload:

```json
{
  "movies": [
    {
      "watched_at": "2026-07-11T00:00:00.000Z",
      "title": "Example Movie",
      "year": 2024,
      "ids": {}
    }
  ]
}
```

Example episode history payload:

```json
{
  "shows": [
    {
      "title": "Example Show",
      "year": 2024,
      "ids": {},
      "seasons": [
        {
          "number": 1,
          "episodes": [
            {
              "number": 2,
              "watched_at": "2026-07-11T00:00:00.000Z"
            }
          ]
        }
      ]
    }
  ]
}
```

## Matching strategy

Preferred IDs, if available from TiviMate/provider metadata:

```text
trakt
tmdb
imdb
tvdb
```

If only title/year/season/episode are available:

1. For movies, search by title + year and cache selected result.
2. For episodes, search show by title + year, then send season/episode numbers.
3. If confidence is low, queue as unmapped instead of sending wrong history.

## Rate limiting

Handle Trakt `429` by respecting `Retry-After` and leaving the event queued.

## Verification probes run

Unauthenticated probes from this workspace:

```text
https://trakt.docs.apiary.io/ -> 200
https://api.trakt.tv/scrobble/start -> 403 without auth/API headers
https://api.trakt.tv/scrobble/pause -> 403 without auth/API headers
https://api.trakt.tv/scrobble/stop -> 403 without auth/API headers
https://api.trakt.tv/sync/history -> 403 without auth/API headers
https://api.trakt.tv/sync/history/remove -> 403 without auth/API headers
```
