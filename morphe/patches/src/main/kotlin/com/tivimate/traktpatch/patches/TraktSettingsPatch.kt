package com.tivimate.traktpatch.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

private const val EXTENSION_CLASS = "Lcom/tivimate/traktpatch/extension/TraktPatchExtension;"
private const val PROTECTED_APPLICATION = "Lar/tvplayer/tv/ProtectedTvPlayerApplication;"

/**
 * Exact 5.1.6 bootstrap method, present in the unpacked base DEX. Unlike the
 * protected launcher, this method has a callable, stable implementation.
 */
private object ProtectedApplicationOnCreateFingerprint : Fingerprint(
    definingClass = PROTECTED_APPLICATION,
    name = "onCreate",
    returnType = "V"
)

/**
 * Merges the extension and registers its lifecycle observer immediately after
 * Application.onCreate, after Android has initialized the Application runtime.
 * This bypasses Morphe's shared extension helper and its unavailable
 * target-specific utility fingerprint.
 */
@Suppress("unused")
val traktSettingsPatch = bytecodePatch(
    name = "TiviMate Trakt settings/login",
    description = "Adds a D-pad focusable Trakt entry under Settings > Other with Trakt Device Authorization and movie/episode progress sync."
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)
    extendWith("extensions/trakt.mpe")

    execute {
        classDefBy(EXTENSION_CLASS)
        // Instruction 0 invokes Application.onCreate; initialize immediately
        // after it, before DexProtector loads protected UI classes.
        ProtectedApplicationOnCreateFingerprint.method.addInstruction(
            1,
            "invoke-static { p0 }, $EXTENSION_CLASS->initialize(Landroid/content/Context;)V"
        )
    }
}
