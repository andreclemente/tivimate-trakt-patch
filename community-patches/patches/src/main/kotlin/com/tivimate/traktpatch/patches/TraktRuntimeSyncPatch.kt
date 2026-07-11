package com.tivimate.traktpatch.patches

import app.revanced.patcher.patch.bytecodePatch

/**
 * Hooks proven TiviMate runtime playback/progress/watched points and forwards
 * neutral events to the injected Trakt sync queue.
 *
 * No broad SQLite hook is implemented here. Runtime evidence must identify the
 * smallest stable method before this patch mutates bytecode.
 */
@Suppress("unused")
val traktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime sync",
    description = "Scaffold for runtime playback/progress/watched hooks that enqueue Trakt sync events.",
) {
    compatibleWith(tivimateCompatibility)

    dependsOn(traktSettingsPatch)

    execute {
        // TODO after runtime evidence:
        // - match playback/progress/watched method fingerprints;
        // - insert invoke-static calls to TraktPatchExtension / hook bridge;
        // - pass media metadata, progress, duration, and watched state.
    }
}
