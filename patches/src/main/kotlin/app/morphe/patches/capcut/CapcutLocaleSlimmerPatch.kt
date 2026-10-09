package app.morphe.patches.capcut

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File

@Suppress("unused")
val capcutLocaleSlimmerPatch = resourcePatch(
    name = "Locale Slimmer",
    description = "Strips unselected language string tables, resources, and asset JSON files from the APK. Base fallback and English are always preserved.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    val targetLocales by stringOption(
        key = "locales",
        title = "Locales to keep",
        description = "Comma-separated language codes to preserve (e.g. 'en, es, pt, fr, de'). Base fallback and English are always retained.",
        default = "en",
        required = false,
    )

    execute {
        val apkRoot = try {
            get("AndroidManifest.xml").parentFile ?: get(".")
        } catch (_: Throwable) {
            get(".")
        }

        val resDir = File(apkRoot, "res")
        val assetsLocalesDir = File(apkRoot, "assets/locales")

        val hasRes = resDir.exists() && resDir.isDirectory
        val hasAssetsLocales = assetsLocalesDir.exists() && assetsLocalesDir.isDirectory

        if (!hasRes && !hasAssetsLocales) {
            println("[CapCut Locale Slimmer] Skipped: res and assets/locales directories not found.")
            return@execute
        }

        val selectedLocales = LocaleUtils.parseTargetLocales(targetLocales, defaultLocales = setOf("en", "en-us"))

        var savedBytes = 0L
        var strippedResDirsCount = 0
        var strippedAssetFilesCount = 0

        // Axis 1A: Android Resource Table (res/values-*/)
        if (hasRes) {
            val targetResDirs = LocaleUtils.resolveResourceDirectories(resDir)
            for (rDir in targetResDirs) {
                val subDirs = rDir.listFiles { f -> f.isDirectory } ?: continue

                for (dir in subDirs) {
                    // Only process values directories (e.g. values-es, values-b+es+419)
                    if (!dir.name.startsWith("values")) continue

                    // Base fallback (e.g. "values") must never be stripped
                    if (dir.name == "values") continue

                    // Extract languages; directories without language qualifiers (e.g. values-night, values-v31) are preserved
                    val languages = LocaleUtils.extractResourceLanguages(dir.name)
                    if (languages.isEmpty()) continue

                    // Preserve if any extracted language matches the user's selected locales
                    if (languages.any { it in selectedLocales }) continue

                    // Unselected locale directory: calculate size and delete recursively
                    val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    if (dir.deleteRecursively()) {
                        savedBytes += dirSize
                        strippedResDirsCount++
                    }
                }
            }
        }

        // Axis 1B: CapCut Asset Localization Bundles (assets/locales/*.json)
        if (hasAssetsLocales) {
            val jsonFiles = assetsLocalesDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            if (jsonFiles != null) {
                for (file in jsonFiles) {
                    val baseName = file.nameWithoutExtension.lowercase()

                    // English fallback is strictly preserved
                    if (baseName == "en" || baseName.startsWith("en-") || baseName.startsWith("en_")) {
                        continue
                    }

                    // Extract base language prefix (e.g. "es-419" -> "es", "ar-arab" -> "ar")
                    val langPrefix = baseName.substringBefore("-").substringBefore("_")
                    if (baseName in selectedLocales || langPrefix in selectedLocales) {
                        continue
                    }

                    val fileSize = file.length()
                    if (file.delete()) {
                        savedBytes += fileSize
                        strippedAssetFilesCount++
                    }
                }
            }
        }

        val totalStripped = strippedResDirsCount + strippedAssetFilesCount
        if (totalStripped == 0) {
            println("[CapCut Locale Slimmer] No unselected locale files found to strip (retained locales: ${selectedLocales.sorted().joinToString(", ")}).")
            return@execute
        }

        val savedFormatted = LocaleUtils.formatBytes(savedBytes)
        println("[CapCut Locale Slimmer] Stripped $totalStripped unselected locale items ($strippedResDirsCount res dirs, $strippedAssetFilesCount asset files) -> Saved $savedFormatted (retained locales: ${selectedLocales.sorted().joinToString(", ")})")
    }
}
