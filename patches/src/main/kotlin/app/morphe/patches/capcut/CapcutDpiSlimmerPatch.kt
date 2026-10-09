package app.morphe.patches.capcut

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import org.w3c.dom.Element
import java.io.File

private val DENSITY_TOKENS = setOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "tvdpi")
private val UI_MODE_PURGE_TOKENS = setOf("watch", "television", "car", "vrheadset")

@Suppress("unused")
val capcutDpiSlimmerPatch = resourcePatch(
    name = "Screen Density Slimmer",
    description = "Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    val targetDensity by stringOption(
        key = "density",
        title = "Target screen density",
        description = "Screen density to preserve (e.g. 'xxhdpi', 'xhdpi', 'hdpi', 'mdpi', 'xxxhdpi'). Single-density orphans, launcher icons, and nodpi/anydpi are always protected.",
        default = "xxhdpi",
        required = false,
    )

    execute {
        val apkRoot = try {
            get("AndroidManifest.xml").parentFile ?: get(".")
        } catch (_: Throwable) {
            get(".")
        }

        val resDir = File(apkRoot, "res")
        if (!resDir.exists() || !resDir.isDirectory) {
            println("[CapCut DPI Slimmer] Skipped: res directory not found.")
            return@execute
        }

        val targetResDirs = LocaleUtils.resolveResourceDirectories(resDir)
        val selectedDensities = (targetDensity ?: "xxhdpi")
            .split(",")
            .map { it.trim().lowercase().removePrefix("drawable-").removePrefix("mipmap-") }
            .filter { it.isNotEmpty() }
            .toSet()
            .ifEmpty { setOf("xxhdpi") }

        // Gate 1: Launcher icons protection - read AndroidManifest.xml
        val protectedIconNames = mutableSetOf("ic_launcher", "ic_launcher_round")
        val manifestFile = File(apkRoot, "AndroidManifest.xml")
        if (manifestFile.exists() && manifestFile.isFile) {
            try {
                document(manifestFile.absolutePath).use { doc ->
                    val elements = mutableListOf<Element>()
                    val appNodes = doc.getElementsByTagName("application")
                    for (i in 0 until appNodes.length) {
                        (appNodes.item(i) as? Element)?.let { elements.add(it) }
                    }
                    val actNodes = doc.getElementsByTagName("activity")
                    for (i in 0 until actNodes.length) {
                        (actNodes.item(i) as? Element)?.let { elements.add(it) }
                    }
                    val aliasNodes = doc.getElementsByTagName("activity-alias")
                    for (i in 0 until aliasNodes.length) {
                        (aliasNodes.item(i) as? Element)?.let { elements.add(it) }
                    }

                    for (el in elements) {
                        for (attr in listOf("android:icon", "android:roundIcon", "icon", "roundIcon")) {
                            val iconVal = el.getAttribute(attr)
                            if (iconVal.isNotBlank() && !iconVal.startsWith("@0x")) {
                                val iconName = iconVal.substringAfterLast('/')
                                if (iconName.isNotBlank()) {
                                    protectedIconNames.add(iconName)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // If XML parsing fails, fallback to default protected icons
            }
        }

        var savedBytes = 0L
        var purgedUiModeDirs = 0
        var trimmedFilesCount = 0

        for (rDir in targetResDirs) {
            val allDirs = rDir.listFiles { f -> f.isDirectory } ?: continue

            // Phase 1: Purge non-phone UI mode directories (e.g. drawable-watch, layout-watch)
            for (dir in allDirs) {
                if (dir.name.startsWith("values")) continue
                val qualifiers = dir.name.split("-").drop(1).map { it.lowercase() }
                if (qualifiers.any { it in UI_MODE_PURGE_TOKENS }) {
                    val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    if (dir.deleteRecursively()) {
                        savedBytes += dirSize
                        purgedUiModeDirs++
                    }
                }
            }

            // Phase 2: Density trimming across drawable and mipmap families
            for (prefix in listOf("drawable", "mipmap")) {
                val familyDirs = (rDir.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) } ?: emptyArray()).toList()

                // Identify retained directories for this family:
                // - Density-independent: nodpi, anydpi
                // - Selected target density directories (e.g. drawable-xxhdpi, mipmap-xxhdpi)
                // - Base fallback directories without any density token (e.g. drawable, drawable-v21)
                val retainedDirs = familyDirs.filter { dir ->
                    val qualifiers = dir.name.split("-").drop(1).map { it.lowercase() }
                    val densityToken = qualifiers.firstOrNull { it in DENSITY_TOKENS }
                    dir.name.contains("nodpi") ||
                        dir.name.contains("anydpi") ||
                        (densityToken != null && densityToken in selectedDensities) ||
                        densityToken == null
                }

                // Inventory all file names present across retained directories
                val retainedFileNames = mutableSetOf<String>()
                for (dir in retainedDirs) {
                    val files = dir.listFiles { f -> f.isFile } ?: continue
                    for (file in files) {
                        retainedFileNames.add(file.name)
                    }
                }

                // Candidate unselected density directories
                val unselectedDirs = familyDirs.filter { dir ->
                    val qualifiers = dir.name.split("-").drop(1).map { it.lowercase() }
                    val densityToken = qualifiers.firstOrNull { it in DENSITY_TOKENS }
                    !dir.name.contains("nodpi") &&
                        !dir.name.contains("anydpi") &&
                        densityToken != null &&
                        densityToken !in selectedDensities
                }

                for (dir in unselectedDirs) {
                    val files = dir.listFiles { f -> f.isFile } ?: continue
                    for (file in files) {
                        val baseName = file.nameWithoutExtension

                        // Gate 1: Protect launcher icons across all densities
                        if (protectedIconNames.any { baseName.equals(it, ignoreCase = true) || baseName.startsWith("${it}_") }) {
                            continue
                        }

                        // Gate 2: In-situ preservation of single-density orphan assets
                        if (file.name !in retainedFileNames) {
                            retainedFileNames.add(file.name)
                            continue
                        }

                        // File exists in retained directory and is not a launcher icon -> safe to delete
                        val fileSize = file.length()
                        if (file.delete()) {
                            savedBytes += fileSize
                            trimmedFilesCount++
                        }
                    }

                    // Remove directory if empty after trimming
                    val remaining = dir.listFiles()
                    if (remaining != null && remaining.isEmpty()) {
                        dir.delete()
                    }
                }
            }
        }

        if (trimmedFilesCount == 0 && purgedUiModeDirs == 0) {
            println("[CapCut DPI Slimmer] No non-target density assets or UI-mode directories found to trim (target: ${selectedDensities.joinToString(", ")}).")
            return@execute
        }

        val savedFormatted = LocaleUtils.formatBytes(savedBytes)
        println("[CapCut DPI Slimmer] Trimmed $trimmedFilesCount density assets, purged $purgedUiModeDirs UI-mode directories -> Saved $savedFormatted (target: ${selectedDensities.joinToString(", ")})")
    }
}
