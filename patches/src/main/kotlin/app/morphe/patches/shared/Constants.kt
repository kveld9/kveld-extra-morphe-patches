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
 * const val EXAMPLE_INPUT_SHA256 = ""
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
    const val INSTAGRAM_INPUT_SHA256 = "b3075e2bb88b14ced55399b825b884b5d90c9945096833fbb0e4a2befd16ebad"

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
    const val TWITTER_TARGET_VERSION = "12.33.0-prod.01"
    const val TWITTER_INPUT_SHA256 = "553c4e4ae99851d28b376fbd3677e4bb0ce4cdc0839d1c0597263da5eadc25b6"

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
    const val MOOVIT_INPUT_SHA256 = "135e7a1964dd71395e6e2f00625d555d9efddb6c1d0b320dffd6f0bb12e4863d"

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
    const val POWERPOINT_INPUT_SHA256 = "e9379a409f0cc72ee0815c23a50852248384e0c65b2f83bcdc8748760456b937"

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
    const val LRMOBILE_INPUT_SHA256 = "019620c4b558de549ae2b91536572b2f5fdd5b962eaca6b5319e7f81b7d4873a"

    val COMPATIBILITY_LRMOBILE = Compatibility(
        name = "Lightroom",
        packageName = LRMOBILE_PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0x001C34,
        targets = listOf(
            AppTarget(
                version = LRMOBILE_TARGET_VERSION,
                description = "Download com.adobe.lrmobile v\$LRMOBILE_TARGET_VERSION (APK, PairIP-free) from Uptodown",
            )
        )
    )

    const val CAPCUT_PACKAGE_NAME = "com.lemon.lvoverseas"
    const val CAPCUT_TARGET_VERSION = "19.7.0"
    const val CAPCUT_INPUT_SHA256 = "b12e11cd13311b85000d0de330a7bdda245151958c01fc5316b8c42c3d0f3cdd"

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

    const val OFFICEHUB_PACKAGE_NAME = "com.microsoft.office.officehubrow"
    const val OFFICEHUB_TARGET_VERSION = "16.0.20527.20022"
    const val OFFICEHUB_INPUT_SHA256 = ""

    val COMPATIBILITY_OFFICEHUB = Compatibility(
        name = "Copilot",
        packageName = OFFICEHUB_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x109CF0,
        targets = listOf(
            AppTarget(
                version = OFFICEHUB_TARGET_VERSION,
                description = "Download com.microsoft.office.officehubrow v\$OFFICEHUB_TARGET_VERSION (APKM) from APKMirror",
            )
        )
    )
}
