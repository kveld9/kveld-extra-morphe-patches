package app.morphe.patches.moovit

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File

@Suppress("unused")
val moovitLocaleSlimmerPatch = resourcePatch(
    name = "Locale Slimmer",
    description = "Strips unselected language string tables and resources from base APK. Base fallback and English are always preserved.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_MOOVIT)

    val targetLocales by stringOption(
        key = "locales",
        title = "Locales to keep",
        description = "Comma-separated language codes to preserve (e.g. 'en, es, pt, fr, de'). Base fallback and English are always retained.",
        default = "en",
        required = false,
    )

    execute {
        val resDir = get("res")
        if (!resDir.exists() || !resDir.isDirectory) {
            println("[Moovit Locale Slimmer] Skipped: res directory not found.")
            return@execute
        }

        val targetResDirs = LocaleUtils.resolveResourceDirectories(resDir)
        val selectedLocales = LocaleUtils.parseTargetLocales(targetLocales, defaultLocales = setOf("en", "en-us"))

        var savedBytes = 0L
        var strippedCount = 0

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
                    strippedCount++
                }
            }
        }

        val savedFormatted = LocaleUtils.formatBytes(savedBytes)
        println("[Moovit Locale Slimmer] Stripped $strippedCount unselected locale directories -> Saved $savedFormatted (retained locales: ${selectedLocales.sorted().joinToString(", ")})")
    }
}
