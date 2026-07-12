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
            // Official APK from https://tivimate.com/apk inspected on 2026-07-12:
            // versionName 5.3.3, versionCode 1000005332,
            // sha256 beaebb25bf818450bf496d4cefb16885b190f156815dea280c31eaae0909ee6c.
            // This is the only stable smoke-test target. Older 5.1.x / 8K-modified
            // builds are intentionally left experimental until the official build opens
            // cleanly after a no-op Morphe rebuild/sign cycle.
            AppTarget(version = "5.3.3", minSdk = 23),
            AppTarget(version = "5.1.6", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.1.5", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.1.0", minSdk = 21, isExperimental = true),
            AppTarget(version = "5.0.4", minSdk = 21, isExperimental = true),
            AppTarget(version = "4.7.0", minSdk = 21, isExperimental = true),
        )
    )
}
