package app.morphe.patches.shared

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
object Constants
