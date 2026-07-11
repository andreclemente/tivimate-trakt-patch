# Sync Queue Design

## Purpose

The queue decouples TiviMate runtime hooks from Trakt network calls. Hooks should emit events quickly and return. A background worker processes queued events.

## Requirements

- No network work on UI/player/SQLite hook threads.
- Durable across app/process restarts.
- Idempotent enough to tolerate duplicate hook events.
- Ordered enough to avoid replaying stale progress after watched/unwatched.
- Safe: no provider stream URLs, credentials, or tokens in event payloads.

## Queue record

See `sync-core/queue.schema.json`.

Core fields:

```text
queue_id
status: pending|in_flight|succeeded|failed|dead
attempt_count
next_attempt_at_ms
created_at_ms
updated_at_ms
event
last_error
```

## Dedupe rules

Use `event.event_id` as the main idempotency key.

Recommended event id format:

```text
<tivimate-install-id>:<media_type>:<provider_item_id-or-normalized-title>:<event_type>:<bucket>
```

Progress events should bucket by time/progress to avoid spamming Trakt:

```text
movie:provider123:progress:percent-10
movie:provider123:progress:percent-20
```

Watched/unwatched events should be stronger than progress:

- A newer `watched` event cancels older pending progress for the same item.
- A newer `unwatched` event cancels older pending watched/progress for the same item.
- A newer progress event may replace older pending progress for the same item if neither has been sent.

## Retry policy

Suggested default:

```text
attempt 1: immediate
attempt 2: +30s
attempt 3: +2m
attempt 4: +10m
attempt 5: +1h
attempt 6+: +6h, then mark dead after max attempts
```

Retry on:

- network unavailable
- timeout
- Trakt 429 with `Retry-After`
- Trakt 5xx

Do not retry without user action on:

- invalid credentials after refresh fails
- malformed event
- unmappable media after search attempts exhausted

## Processing loop

1. Load due `pending`/retryable records.
2. Mark one record `in_flight`.
3. Send to Trakt.
4. On success, mark `succeeded` and store response summary.
5. On retryable failure, update `attempt_count`, `last_error`, `next_attempt_at_ms`.
6. On permanent failure, mark `dead`.

## Android storage options

Prefer a small private SQLite DB or Room DB owned by the integration/module, separate from TiviMate's DB.

Avoid modifying TiviMate's own DB for queue storage.
