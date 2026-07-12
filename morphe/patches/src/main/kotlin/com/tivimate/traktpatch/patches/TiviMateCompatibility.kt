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
            // 8K-modified APK inspected locally: versionName 5.1.6, versionCode 5161.
            // The official 5.3.3 APK currently triggers a Morphe Manager/apkzlib
            // `ExtraField$AlignmentSegment` failure before patch code runs, so the
            // active smoke-test route is back on 8K/5.1.6 until Manager supports the
            // official raw APK without a pre-normalized input.
            AppTarget(version = "5.1.6", minSdk = 21),
            AppTarget(version = "5.3.3", minSdk = 23, isExperimental = true),
            AppTarget(version = "5.1.5", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.1.0", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.0.4", minSdk = 21, isExperimental = true),
            AppTarget(version = "4.7.0", minSdk = 21, isExperimental = true),
        )
    )
}
