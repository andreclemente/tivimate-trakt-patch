package com.tivimate.traktpatch.patches

/**
 * Fingerprint placeholders.
 *
 * Fill these only after evidence from JADX/apktool/Frida/emulator logs.
 */
internal object RequiredFingerprints {
    const val SETTINGS_MENU = "TiviMateSettingsMenuFingerprint"
    const val PLAYBACK_PROGRESS = "TiviMatePlaybackProgressUpdateFingerprint"
    const val WATCHED_STATE = "TiviMateWatchedStateUpdateFingerprint"
    const val MEDIA_METADATA = "TiviMateMediaMetadataAccessorFingerprint"
}
