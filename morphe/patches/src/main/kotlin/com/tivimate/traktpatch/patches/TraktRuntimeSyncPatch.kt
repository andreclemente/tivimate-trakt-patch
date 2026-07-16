package com.tivimate.traktpatch.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

private const val PROGRESS_BRIDGE =
    "Lcom/tivimate/traktpatch/extension/TraktProgressBridge;"
private const val ON_DEMAND_BRIDGE =
    "Lcom/tivimate/traktpatch/extension/TraktOnDemandBridge;"
private const val XTREAM_CODES = "Lـﹶ/ـﹶ;"
private const val XTREAM_PARAMS =
    "Lar/tvplayer/core/data/api/xtreamcodes/XtreamCodes\u0024Params;"

/**
 * TiviMate 5.1.6's SupportSQLite wrapper method that delegates to
 * SQLiteDatabase.endTransaction(). At instruction 2, v0 still contains the
 * database whose outer transaction has just ended.
 */
private object EndTransactionFingerprint : Fingerprint(
    definingClass = "Lʿﹶ/ﹳﹳ;",
    name = "ˏᴵ",
    returnType = "V"
)

private object XtreamVodInfoFingerprint : Fingerprint(
    definingClass = XTREAM_CODES,
    name = "ˈٴ",
    returnType = "Lar/tvplayer/core/data/api/xtreamcodes/VodInfo;",
    parameters = listOf(XTREAM_PARAMS, "I")
)

private object XtreamSeriesInfoFingerprint : Fingerprint(
    definingClass = XTREAM_CODES,
    name = "ﾞˊ",
    returnType = "Lar/tvplayer/core/data/api/xtreamcodes/SeriesInfo;",
    parameters = listOf(XTREAM_PARAMS, "I")
)

/**
 * Runtime sync: observe committed changes in TiviMate's playback-position
 * tables, resolve stable Xtream metadata, then scrobble through the Worker
 * semantics.
 */
@Suppress("unused")
val traktRuntimeSyncPatch = bytecodePatch(
    name = "TiviMate Trakt runtime progress sync",
    description = "Syncs committed movie and episode playback progress to Trakt using stable provider metadata.",
    default = true
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
    extendWith("extensions/trakt.mpe")

    execute {
        classDefBy(PROGRESS_BRIDGE)
        classDefBy(ON_DEMAND_BRIDGE)
        // Existing instructions: iget-object v0, invoke endTransaction, return.
        EndTransactionFingerprint.method.addInstruction(
            2,
            "invoke-static { v0 }, $PROGRESS_BRIDGE->onTransactionEnded(Landroid/database/sqlite/SQLiteDatabase;)V"
        )
        XtreamVodInfoFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1 }, $ON_DEMAND_BRIDGE->onVodInfoRequested(Ljava/lang/Object;I)V"
        )
        XtreamSeriesInfoFingerprint.method.addInstruction(
            0,
            "invoke-static { p0, p1 }, $ON_DEMAND_BRIDGE->onSeriesInfoRequested(Ljava/lang/Object;I)V"
        )
    }
}
