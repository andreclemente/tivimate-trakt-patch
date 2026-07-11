package com.tivimate.traktpatch.patches

/** Fill only after evidence from JADX/apktool/Frida/TV logs. */
internal object RequiredFingerprints {
    const val SETTINGS_MENU = "TiviMateSettingsMenuFingerprint"
    const val PLAYBACK_PROGRESS = "TiviMatePlaybackProgressUpdateFingerprint"
    const val WATCHED_STATE = "TiviMateWatchedStateUpdateFingerprint"
    const val MEDIA_METADATA = "TiviMateMediaMetadataAccessorFingerprint"
}
