package com.tivimate.traktpatch.patches

import app.morphe.patcher.patch.bytecodePatch
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

/** Reserved until watched/progress storage and callback hooks are proven. */
@Suppress("unused")
val traktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime sync",
    description = "Reserved production patch; watched/progress sync is disabled until stable hooks are proven.",
    default = false
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
}
