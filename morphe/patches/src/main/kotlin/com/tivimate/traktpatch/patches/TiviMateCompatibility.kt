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
        targets = emptyList<AppTarget>(),
    )
}
