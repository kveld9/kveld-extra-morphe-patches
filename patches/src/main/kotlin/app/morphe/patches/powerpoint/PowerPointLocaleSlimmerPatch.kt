package app.morphe.patches.powerpoint

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File

@Suppress("unused")
val powerPointLocaleSlimmerPatch = resourcePatch(
    name = "PowerPoint Locale Slimmer",
    description = "Strips unselected localized resource directories (res/values-<locale>/) from PowerPoint base APK. English (en, en-us) is always retained. WARNING: Application strings for stripped locales will fall back to English.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_POWERPOINT)

    val targetLocales by stringOption(
        key = "locales",
        title = "Locales to keep",
        description = "Comma-separated language codes to preserve (e.g. 'en, es, fr, de'). English fallback (en, en-us) is always retained.",
        default = "en",
        required = false,
    )

    execute {
        val mainRes = get("res")
        if (!mainRes.exists() || !mainRes.isDirectory) {
            println("[PowerPoint Locale Slimmer] Skipped: res directory not found.")
            return@execute
        }

        val retained = LocaleUtils.parseTargetLocales(targetLocales)
        val resDirs = LocaleUtils.resolveResourceDirectories(mainRes)

        var totalPrunedDirs = 0
        var totalSavedBytes = 0L

        for (resDir in resDirs) {
            val result = pruneLocaleSubdirectories(resDir, retained)
            totalPrunedDirs += result.prunedCount
            totalSavedBytes += result.savedBytes
        }

        val formattedSavings = LocaleUtils.formatBytes(totalSavedBytes)
        println("[PowerPoint Locale Slimmer] Pruned $totalPrunedDirs localized resource directories -> Saved $formattedSavings.")
    }
}

private data class LocalePruneResult(val prunedCount: Int, val savedBytes: Long)

private fun pruneLocaleSubdirectories(resDir: File, retained: Set<String>): LocalePruneResult {
    val subDirs = resDir.listFiles { f -> f.isDirectory } ?: return LocalePruneResult(0, 0L)
    var count = 0
    var bytes = 0L

    for (dir in subDirs) {
        if (!isUnwantedLocaleDir(dir, retained)) continue
        val dirSize = measureDirectoryBytes(dir)
        if (dir.deleteRecursively()) {
            count++
            bytes += dirSize
        }
    }
    return LocalePruneResult(count, bytes)
}

private fun isUnwantedLocaleDir(dir: File, retained: Set<String>): Boolean {
    if (!dir.name.startsWith("values-")) return false
    val languages = LocaleUtils.extractResourceLanguages(dir.name)
    if (languages.isEmpty()) return false
    return languages.none { it in retained }
}

private fun measureDirectoryBytes(dir: File): Long {
    var size = 0L
    dir.walkTopDown().forEach { file ->
        if (file.isFile) {
            size += file.length()
        }
    }
    return size
}
