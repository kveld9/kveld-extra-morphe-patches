package app.morphe.patches.powerpoint

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File
import org.w3c.dom.Element

@Suppress("unused")
val powerPointDpiSlimmerPatch = resourcePatch(
    name = "PowerPoint DPI Slimmer",
    description = "Strips drawables for unselected screen densities from PowerPoint base APK while preserving launcher icons and single-density assets. WARNING: Displays matching stripped densities will scale preserved assets.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_POWERPOINT)

    val targetDpi by stringOption(
        key = "dpi",
        title = "Target screen density",
        description = "Screen density to preserve (ldpi, mdpi, tvdpi, hdpi, xhdpi, xxhdpi, xxxhdpi). Default is xxhdpi.",
        default = "xxhdpi",
        required = false,
    )

    execute {
        val mainRes = get("res")
        if (!mainRes.exists() || !mainRes.isDirectory) {
            println("[PowerPoint DPI Slimmer] Skipped: res directory not found.")
            return@execute
        }

        val normalizedTarget = normalizeTargetDpi(targetDpi)
        val protectedIcons = resolveProtectedIcons(get("AndroidManifest.xml"))
        val resDirs = LocaleUtils.resolveResourceDirectories(mainRes)

        var totalPrunedFiles = 0
        var totalPrunedDirs = 0
        var totalSavedBytes = 0L

        for (resDir in resDirs) {
            val result = pruneDpiInResourceDir(resDir, normalizedTarget, protectedIcons)
            totalPrunedFiles += result.prunedFiles
            totalPrunedDirs += result.prunedDirs
            totalSavedBytes += result.savedBytes
        }

        val formattedSavings = LocaleUtils.formatBytes(totalSavedBytes)
        println("[PowerPoint DPI Slimmer] Pruned $totalPrunedFiles drawables across $totalPrunedDirs directories -> Saved $formattedSavings.")
    }
}

private val KNOWN_DPI_QUALIFIERS = setOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
private val NON_PHONE_UI_MODES = setOf("watch", "television", "car", "vrheadset")
private val FALLBACK_PROTECTED_ICONS = setOf("ic_powerpoint", "ic_launcher", "ic_launcher_round")

private data class DpiPruneResult(val prunedFiles: Int, val prunedDirs: Int, val savedBytes: Long)

private fun normalizeTargetDpi(rawInput: String?): String {
    val clean = (rawInput ?: "").trim().lowercase().removePrefix("drawable-").removePrefix("mipmap-")
    return if (clean in KNOWN_DPI_QUALIFIERS) clean else "xxhdpi"
}

private fun resolveProtectedIcons(manifestFile: File): Set<String> {
    val icons = mutableSetOf<String>()
    icons.addAll(FALLBACK_PROTECTED_ICONS)
    if (!manifestFile.exists() || !manifestFile.isFile) return icons

    try {
        val docBuilder = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(manifestFile)
        collectIconAttributes(doc.documentElement, icons)
    } catch (_: Exception) {
        // Fallback set is already populated
    }
    return icons
}

private fun collectIconAttributes(root: Element, outIcons: MutableSet<String>) {
    val tagNames = listOf("application", "activity", "activity-alias")
    for (tag in tagNames) {
        val elements = root.getElementsByTagName(tag)
        for (i in 0 until elements.length) {
            val elem = elements.item(i) as? Element ?: continue
            addIconNameIfPresent(elem.getAttribute("android:icon"), outIcons)
            addIconNameIfPresent(elem.getAttribute("android:roundIcon"), outIcons)
        }
    }
}

private fun addIconNameIfPresent(attrValue: String, outIcons: MutableSet<String>) {
    if (!attrValue.startsWith("@")) return
    val slash = attrValue.indexOf('/')
    if (slash != -1 && slash < attrValue.length - 1) {
        outIcons.add(attrValue.substring(slash + 1))
    }
}

private fun pruneDpiInResourceDir(
    resDir: File,
    targetDpi: String,
    protectedIcons: Set<String>,
): DpiPruneResult {
    val subDirs = resDir.listFiles { f -> f.isDirectory } ?: return DpiPruneResult(0, 0, 0L)
    val retainedAssetNames = collectRetainedAssetNames(subDirs, targetDpi)

    var prunedFiles = 0
    var prunedDirs = 0
    var savedBytes = 0L

    for (dir in subDirs) {
        val result = pruneSingleDpiSubDir(dir, targetDpi, protectedIcons, retainedAssetNames)
        prunedFiles += result.prunedFiles
        prunedDirs += result.prunedDirs
        savedBytes += result.savedBytes
    }

    return DpiPruneResult(prunedFiles, prunedDirs, savedBytes)
}

private fun pruneSingleDpiSubDir(
    dir: File,
    targetDpi: String,
    protectedIcons: Set<String>,
    retainedAssets: Set<String>,
): DpiPruneResult {
    if (shouldPurgeNonPhoneDir(dir)) {
        val dirBytes = measureDirectoryBytes(dir)
        val prunedDirs = if (dir.deleteRecursively()) 1 else 0
        return DpiPruneResult(0, prunedDirs, dirBytes)
    }
    return pruneUnselectedDpiDir(dir, targetDpi, protectedIcons, retainedAssets)
}

private fun shouldPurgeNonPhoneDir(dir: File): Boolean {
    if (dir.name.startsWith("values")) return false
    val qualifiers = dir.name.split("-").drop(1)
    return qualifiers.any { it in NON_PHONE_UI_MODES }
}

private fun collectRetainedAssetNames(subDirs: Array<File>, targetDpi: String): Set<String> {
    val retainedNames = mutableSetOf<String>()
    for (dir in subDirs) {
        if (!isRetainedDensityDir(dir.name, targetDpi)) continue
        val files = dir.listFiles { f -> f.isFile } ?: continue
        for (file in files) {
            retainedNames.add(extractBaseAssetName(file.name))
        }
    }
    return retainedNames
}

private fun isRetainedDensityDir(dirName: String, targetDpi: String): Boolean {
    if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) return false
    if (dirName in setOf("drawable", "mipmap")) return true
    if (dirName.contains("-nodpi") || dirName.contains("-anydpi")) return true
    return dirName.contains("-$targetDpi")
}

private fun extractBaseAssetName(fileName: String): String {
    return fileName.substringBefore('.').removeSuffix(".9")
}

private fun pruneUnselectedDpiDir(
    dir: File,
    targetDpi: String,
    protectedIcons: Set<String>,
    retainedAssets: Set<String>,
): DpiPruneResult {
    if (!isRemovableDensityDir(dir.name, targetDpi)) return DpiPruneResult(0, 0, 0L)
    val files = dir.listFiles { f -> f.isFile } ?: return DpiPruneResult(0, 0, 0L)

    val (deletedFiles, savedBytes) = deleteRemovableFiles(files, protectedIcons, retainedAssets)
    val deletedDirs = if (dir.listFiles()?.isEmpty() == true && dir.delete()) 1 else 0

    return DpiPruneResult(deletedFiles, deletedDirs, savedBytes)
}

private fun deleteRemovableFiles(
    files: Array<File>,
    protectedIcons: Set<String>,
    retainedAssets: Set<String>,
): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    for (file in files) {
        val baseName = extractBaseAssetName(file.name)
        if (baseName in protectedIcons || baseName !in retainedAssets) continue
        val fileSize = file.length()
        if (file.delete()) {
            count++
            bytes += fileSize
        }
    }
    return Pair(count, bytes)
}

private fun isRemovableDensityDir(dirName: String, targetDpi: String): Boolean {
    if (!dirName.startsWith("drawable-") && !dirName.startsWith("mipmap-")) return false
    if (dirName.contains("-nodpi") || dirName.contains("-anydpi")) return false
    if (dirName.contains("-$targetDpi")) return false
    val qualifiers = dirName.split("-").drop(1)
    if ("ldrtl" in qualifiers || "ldltr" in qualifiers) return false
    return qualifiers.any { it in KNOWN_DPI_QUALIFIERS }
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
