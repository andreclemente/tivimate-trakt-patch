package com.tivimate.traktpatch.patches

import app.morphe.patcher.patch.bytecodePatch
import com.tivimate.traktpatch.patches.Constants.COMPATIBILITY_TIVIMATE

private const val EXTENSION_CLASS = "Lcom/tivimate/traktpatch/extension/TraktPatchExtension;"

@Suppress("unused")
val traktSettingsPatch = bytecodePatch(
    name = "TiviMate Trakt settings/login",
    description = "Scaffold for Trakt device-code login and sync settings inside TiviMate."
) {
    compatibleWith(COMPATIBILITY_TIVIMATE)

    execute {
        // TODO after mapping settings UI:
        // - inject/enable extension classes;
        // - add Trakt settings row / launch TraktSettingsActivity;
        // - support TV D-pad and phone/tablet touch;
        // - wire device-code OAuth status UI.
        @Suppress("UNUSED_VARIABLE")
        val extensionClass = EXTENSION_CLASS
    }
}
