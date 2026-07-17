# Verification gate

A release is complete only when all gates pass:

1. Python behavior/source regression suite.
2. Worker Node test suite.
3. Clean Gradle extension and Android patch-bundle build.
4. Exact Morphe application to TiviMate 5.1.6 with both patches.
5. APK signature verification.
6. Morphe Manager source refresh, patch, Android update, and launch.
7. Real movie playback producing an accepted authenticated Trakt scrobble.
8. Real episode playback producing an accepted authenticated Trakt scrobble.
9. Runtime log check showing no provider URL, credential, or token markers.
10. Remote descriptor and artifact byte-for-byte verification.
11. Upgrade install preserves authorization and native watched/resume state.
12. Imported writes produce no outbound Trakt echo; later local playback does.
13. Force-stop/restart and device reboot preserve full watched and partial-resume state.
14. Repeated detail opens remain bounded and coalesce duplicate work.
15. Connected-state disconnect confirmation is D-pad visible; revocation behavior is covered without exposing credentials.

Expected sanitized runtime evidence:

```text
progress changed table=movies count=1
resolved movie metadata tmdb=true ...
movie scrobble accepted action=pause ...

progress changed table=episode_last_played_positions count=1
resolved episode metadata ... season=true number=true
episode scrobble accepted action=pause ...
```

Never preserve raw log lines containing provider URLs or credentials.
