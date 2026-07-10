# History/progress findings

Status: Not proven. Static APK inventory found likely database infrastructure but no confirmed watched/progress table or field yet.

## Static string evidence

Evidence: `research/findings/initial-string-hit-counts.txt`, `research/strings/Tivi8KPro.strings.txt`

Relevant user-visible strings exist:

```text
Clear history?
No history
Search history
Show search history
Highlight progress only
Delay before adding to history, sec
Reset positions
```

These prove TiviMate has history/progress/position UX concepts, but do **not** prove the storage location.

## Current confirmed storage candidates

- `Lar/tvplayer/core/data/db/TvPlayerDatabase;`
- `Lar/tvplayer/core/data/db/TvPlayerDatabase_Impl;`

## Unknown

- Which table stores movie watched state.
- Which table stores episode watched state.
- Which column stores playback position.
- Which column stores duration/progress percentage.
- Whether live TV history shares the same storage as VOD progress.

## Required proof for milestone

Milestone is not complete until controlled tests demonstrate values changing for:

- Start playback.
- Pause playback.
- Resume playback.
- Seek.
- Stop playback.
- Finish playback.
- Mark watched.
- Mark unwatched.

Preferred proof path:

1. Pull database before action.
2. Perform one controlled action.
3. Pull database after action.
4. Compare schema/dump/WAL.
5. Confirm UI state after restart.
