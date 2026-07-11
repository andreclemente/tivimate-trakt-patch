# Sync Core

This directory defines the TiviMate-independent sync contract.

Runtime hooks should emit `events.schema.json` events. The queue should persist `queue.schema.json` records until the Trakt client confirms success.

## Event examples

- `examples/movie-progress.json`
- `examples/episode-watched.json`

## Privacy rule

Events must not contain provider credentials, usernames, passwords, authorization headers, or full stream URLs.
