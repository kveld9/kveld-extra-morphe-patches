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
    const val INSTAGRAM_TARGET_VERSION = "452.1.0.53.64"
    const val INSTAGRAM_INPUT_SHA256 = "8575bb673c5a26ee4e7ab63f852be2f2f0faed40a73d8a373316d057425351a9"

    val COMPATIBILITY_INSTAGRAM = Compatibility(
        name = "Instagram",
        packageName = INSTAGRAM_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xE1306C,
        targets = listOf(
            AppTarget(
                version = INSTAGRAM_TARGET_VERSION,
                description = "Download com.instagram.android v$INSTAGRAM_TARGET_VERSION (APKM) arm64-v8a 480-640dpi versionCode 385811006 from APKMirror",
            )
        )
    )

    const val TWITTER_PACKAGE_NAME = "com.twitter.android"
    const val TWITTER_TARGET_VERSION = "12.40.1-prod.01"
    const val TWITTER_INPUT_SHA256 = "3886372197a6227b788cbc411ccf19f936b5f83abcd8e3358f9d54caa587e61c"

    val COMPATIBILITY_TWITTER = Compatibility(
        name = "X",
        packageName = TWITTER_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        targets = listOf(
            AppTarget(
                version = TWITTER_TARGET_VERSION,
                description = "Download com.twitter.android v$TWITTER_TARGET_VERSION (APKM) arm64-v8a 120-640dpi versionCode 312401001 (BUNDLE 35 S) from APKMirror",
            )
        )
    )

    const val MOOVIT_PACKAGE_NAME = "com.tranzmate"
    const val MOOVIT_TARGET_VERSION = "5.204.0.1813"
    const val MOOVIT_INPUT_SHA256 = "2d2ef5e75710b3fd16a97def4ff84a263c9446ce929b693a80f02994ba31ab7a"

    val COMPATIBILITY_MOOVIT = Compatibility(
        name = "Moovit",
        packageName = MOOVIT_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1864E4,
        targets = listOf(
            AppTarget(
                version = MOOVIT_TARGET_VERSION,
                description = "Download com.tranzmate v$MOOVIT_TARGET_VERSION (APKM) arm64-v8a 160-640dpi versionCode 1813 (BUNDLE 31 S) from APKMirror",
            )
        )
    )

    const val CAPCUT_PACKAGE_NAME = "com.lemon.lvoverseas"
    const val CAPCUT_TARGET_VERSION = "19.9.0"
    const val CAPCUT_INPUT_SHA256 = "3c74a9941237451953fe4ed1abe477b7413d11d11a4b5b45a17441afd30f2c38"

    val COMPATIBILITY_CAPCUT = Compatibility(
        name = "CapCut",
        packageName = CAPCUT_PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0x000000,
        targets = listOf(
            AppTarget(
                version = CAPCUT_TARGET_VERSION,
                description = "Download com.lemon.lvoverseas v$CAPCUT_TARGET_VERSION (APK) arm64-v8a nodpi versionCode 19900002 from APKMirror",
            )
        )
    )

    const val HEVY_PACKAGE_NAME = "com.hevy"
    const val HEVY_TARGET_VERSION = "3.2.2"
    const val HEVY_INPUT_SHA256 = "a2b94d9b451b7a3d1c3916df5d57cdf10d90659acd1945966405e192507cbe92"

    val COMPATIBILITY_HEVY = Compatibility(
        name = "Hevy - Gym Log Workout Tracker",
        packageName = HEVY_PACKAGE_NAME,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF4500,
        targets = listOf(
            AppTarget(
                version = HEVY_TARGET_VERSION,
                description = "Download com.hevy v$HEVY_TARGET_VERSION (APKM) arm64-v8a 120-640dpi versionCode 3308730 (BUNDLE) from APKMirror",
            )
        )
    )

    const val NOKOPRINT_PACKAGE_NAME = "com.nokoprint"
    const val NOKOPRINT_TARGET_VERSION = "5.28.8"
    const val NOKOPRINT_INPUT_SHA256 = "99eae16361f290a750ba317a9e5abc7b5701f1eb08652417e3fc643698e46a52"

    val COMPATIBILITY_NOKOPRINT = Compatibility(
        name = "NokoPrint - WiFi, Bluetooth, USB",
        packageName = NOKOPRINT_PACKAGE_NAME,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x0288D1,
        targets = listOf(
            AppTarget(
                version = NOKOPRINT_TARGET_VERSION,
                description = "Download com.nokoprint v$NOKOPRINT_TARGET_VERSION (XAPK bundle) from APKPure",
            )
        )
    )

    const val XIAOMI_EARBUDS_PACKAGE_NAME = "com.mi.earphone"
    const val XIAOMI_EARBUDS_TARGET_VERSION = "1.38.0i"
    const val XIAOMI_EARBUDS_INPUT_SHA256 = "cf0f1e7ed1e02d3a76ddaa81110ea385415a4ad5bd4aa41fca38b9da33cf8b85"

    val COMPATIBILITY_XIAOMI_EARBUDS = Compatibility(
        name = "Xiaomi Earbuds",
        packageName = XIAOMI_EARBUDS_PACKAGE_NAME,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFF6700,
        targets = listOf(
            AppTarget(
                version = XIAOMI_EARBUDS_TARGET_VERSION,
                description = "Download com.mi.earphone v$XIAOMI_EARBUDS_TARGET_VERSION (XAPK bundle) from APKPure",
            )
        )
    )

    const val SUBE_PACKAGE_NAME = "com.sube.app"
    const val SUBE_TARGET_VERSION = "2.7.25"
    const val SUBE_INPUT_SHA256 = "87484f3c3f89b79883f9a842f2871ac3a9670d04143e3378d9c4dcf4cc20cad9"

    val COMPATIBILITY_SUBE = Compatibility(
        name = "SUBE",
        packageName = SUBE_PACKAGE_NAME,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x00A4E4,
        targets = listOf(
            AppTarget(
                version = SUBE_TARGET_VERSION,
                description = "Download com.sube.app v$SUBE_TARGET_VERSION (XAPK bundle) from APKPure",
            )
        )
    )

    const val SDMSE_PACKAGE_NAME = "eu.darken.sdmse"
    const val SDMSE_TARGET_VERSION = "2.2.2-rc0"
    const val SDMSE_INPUT_SHA256 = "11b5b9084c27ab3b30db47c92f403129244c178e81e07c0bb0ffbb64a9bb1bb6"

    val COMPATIBILITY_SDMSE = Compatibility(
        name = "SD Maid SE",
        packageName = SDMSE_PACKAGE_NAME,
        apkFileType = ApkFileType.APK,
        appIconColor = 0x4CAF50,
        targets = listOf(
            AppTarget(
                version = SDMSE_TARGET_VERSION,
                description = "Download eu.darken.sdmse v$SDMSE_TARGET_VERSION (APK) FOSS-RELEASE versionCode 20202000",
            )
        )
    )

    const val IBIS_PAINT_PACKAGE_NAME = "jp.ne.ibis.ibispaintx.app"
    const val IBIS_PAINT_TARGET_VERSION = "14.1.2"
    const val IBIS_PAINT_INPUT_SHA256 = "7c52acb5d784eef2d4b9f39c4f83829a5d0ff99cc36d9e23626330be4f55ff12"

    val COMPATIBILITY_IBIS_PAINT = Compatibility(
        name = "IbisPaint X",
        packageName = IBIS_PAINT_PACKAGE_NAME,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x0B649B,
        targets = listOf(
            AppTarget(
                version = IBIS_PAINT_TARGET_VERSION,
                description = "Download jp.ne.ibis.ibispaintx.app v$IBIS_PAINT_TARGET_VERSION (XAPK bundle) from APKPure",
            )
        )
    )
}
