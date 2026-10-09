package app.morphe.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Single source of truth for target apps: package names, the one supported
 * version per app, and the [app.morphe.patcher.patch.Compatibility] contracts
 * consumed by patches through `compatibleWith(...)`.
 *
 * Template for a new target:
 *
 * ```
 * const val EXAMPLE_PACKAGE_NAME = "com.example.app"
 * const val EXAMPLE_TARGET_VERSION = "1.0.0"
 *
 * val COMPATIBILITY_EXAMPLE = Compatibility(
 *     name = "Example",
 *     packageName = EXAMPLE_PACKAGE_NAME,
 *     apkFileType = ApkFileType.APK,
 *     appIconColor = 0x000000,
 *     targets = listOf(
 *         AppTarget(
 *             version = EXAMPLE_TARGET_VERSION,
 *             description = "Download com.example.app v$EXAMPLE_TARGET_VERSION (APK) from APKMirror",
 *         )
 *     )
 * )
 * ```
 */
object Constants {
    const val INSTAGRAM_PACKAGE_NAME = "com.instagram.android"
    const val INSTAGRAM_TARGET_VERSION = "447.0.0.55.81"

    val COMPATIBILITY_INSTAGRAM = Compatibility(
        name = "Instagram",
        packageName = INSTAGRAM_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xE1306C,
        targets = listOf(
            AppTarget(
                version = INSTAGRAM_TARGET_VERSION,
                description = "Download com.instagram.android v\$INSTAGRAM_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )

    const val TWITTER_PACKAGE_NAME = "com.twitter.android"
    const val TWITTER_TARGET_VERSION = "12.19.1-release.0"

    val COMPATIBILITY_TWITTER = Compatibility(
        name = "X",
        packageName = TWITTER_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        targets = listOf(
            AppTarget(
                version = TWITTER_TARGET_VERSION,
                description = "Download com.twitter.android v\$TWITTER_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )

    const val MOOVIT_PACKAGE_NAME = "com.tranzmate"
    const val MOOVIT_TARGET_VERSION = "5.201.1.1809"

    val COMPATIBILITY_MOOVIT = Compatibility(
        name = "Moovit",
        packageName = MOOVIT_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1864E4,
        targets = listOf(
            AppTarget(
                version = MOOVIT_TARGET_VERSION,
                description = "Download com.tranzmate v\$MOOVIT_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )

    const val POWERPOINT_PACKAGE_NAME = "com.microsoft.office.powerpoint"
    const val POWERPOINT_TARGET_VERSION = "16.0.20527.20034"

    val COMPATIBILITY_POWERPOINT = Compatibility(
        name = "PowerPoint",
        packageName = POWERPOINT_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xE44C50,
        targets = listOf(
            AppTarget(
                version = POWERPOINT_TARGET_VERSION,
                description = "Download com.microsoft.office.powerpoint v\$POWERPOINT_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )

    const val LRMOBILE_PACKAGE_NAME = "com.adobe.lrmobile"
    const val LRMOBILE_TARGET_VERSION = "11.6.01"

    val COMPATIBILITY_LRMOBILE = Compatibility(
        name = "Lightroom",
        packageName = LRMOBILE_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x001C34,
        targets = listOf(
            AppTarget(
                version = LRMOBILE_TARGET_VERSION,
                description = "Download com.adobe.lrmobile v\$LRMOBILE_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )

    const val CAPCUT_PACKAGE_NAME = "com.lemon.lvoverseas"
    const val CAPCUT_TARGET_VERSION = "19.7.0"

    val COMPATIBILITY_CAPCUT = Compatibility(
        name = "CapCut",
        packageName = CAPCUT_PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0x000000,
        targets = listOf(
            AppTarget(
                version = CAPCUT_TARGET_VERSION,
                description = "Download com.lemon.lvoverseas v\$CAPCUT_TARGET_VERSION (APK) from APKMirror",
            )
        )
    )
}
