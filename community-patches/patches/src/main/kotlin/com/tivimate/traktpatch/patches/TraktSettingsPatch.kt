package com.tivimate.traktpatch.patches

import app.revanced.patcher.patch.bytecodePatch

/**
 * Adds Trakt login/settings entry points to the patched APK.
 *
 * This patch is intentionally non-invasive until the TiviMate settings menu
 * insertion point is mapped with static/runtime evidence.
 */
@Suppress("unused")
val traktSettingsPatch = bytecodePatch(
    name = "TiviMate Trakt settings/login",
    description = "Scaffold for Trakt device-code login and sync settings inside TiviMate.",
) {
    compatibleWith(tivimateCompatibility)

    extendWith("extensions/tivimate-trakt.rve")

    execute {
        // TODO after mapping settings UI:
        // - add TraktSettingsActivity manifest entry/resources;
        // - patch TiviMate settings list/menu to launch it;
        // - keep UI cross-device: Android TV D-pad/focus support plus
        //   phone/tablet touch targets; no required touchscreen or TV-only shortcut;
        // - wire device-code OAuth status UI.
    }
}
