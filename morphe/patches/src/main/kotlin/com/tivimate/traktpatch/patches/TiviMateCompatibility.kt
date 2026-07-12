package com.tivimate.traktpatch.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

internal object Constants {
    val COMPATIBILITY_TIVIMATE = Compatibility(
        name = "TiviMate",
        packageName = "ar.tvplayer.tv",
        apkFileType = ApkFileType.APK_REQUIRED,
        appIconColor = 0x1E88E5,
        targets = listOf(
            // Local inspected APK: versionName 5.1.6, versionCode 5161.
            // Keep these as visible/non-experimental targets so Morphe Manager shows
            // the app/patches normally. Real hook support is still pending evidence.
            AppTarget(version = "5.1.6", minSdk = 21),
            AppTarget(version = "5.1.5", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.1.0", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.0.4", minSdk = 21, isExperimental = true),
            AppTarget(version = "4.7.0", minSdk = 21, isExperimental = true),
        )
    )
}
