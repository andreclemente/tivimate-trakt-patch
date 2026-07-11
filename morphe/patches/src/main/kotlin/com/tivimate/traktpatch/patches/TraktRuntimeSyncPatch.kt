package com.tivimate.traktpatch.patches

import app.morphe.patcher.patch.bytecodePatch
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

@Suppress("unused")
val traktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime sync",
    description = "Scaffold for runtime playback/progress/watched hooks that enqueue Trakt sync events."
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)

    dependsOn(traktSettingsPatch)

    execute {
        // TODO after runtime evidence:
        // - match playback/progress/watched fingerprints;
        // - insert invoke-static calls to TraktPatchExtension / hook bridge;
        // - pass media metadata, progress, duration, and watched state.
    }
}
