package app.morphe.patches.officehub

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File

@Suppress("unused")
val officeHubJunkCleanerPatch = rawResourcePatch(
    name = "Copilot Junk Cleaner",
    description = "Purges non-functional build metadata, properties, proto descriptors, and duplicate license notices from APK root and META-INF while strictly protecting runtime assets and signatures.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_OFFICEHUB)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        val rootDir = if (manifestFile.exists()) manifestFile.parentFile ?: get(".") else get(".")

        var cleanedFiles = 0
        var savedBytes = 0L

        // 1. Purge root junk build metadata
        val rootFiles = rootDir.listFiles { f -> f.isFile } ?: emptyArray()
        for (file in rootFiles) {
            if (isRootJunkFile(file)) {
                val size = file.length()
                if (file.delete()) {
                    cleanedFiles++
                    savedBytes += size
                }
            }
        }

        // 2. Purge META-INF junk files (duplicate licenses, version files, build metadata)
        val metaInfDir = get("META-INF")
        if (metaInfDir.exists() && metaInfDir.isDirectory) {
            val metaFiles = metaInfDir.walkTopDown().filter { it.isFile }.toList()
            for (file in metaFiles) {
                if (isMetaInfJunkFile(file)) {
                    val size = file.length()
                    if (file.delete()) {
                        cleanedFiles++
                        savedBytes += size
                    }
                }
            }

            // Prune empty subdirectories in META-INF (protecting services directory)
            metaInfDir.listFiles { f -> f.isDirectory }?.forEach { subDir ->
                if (subDir.name != "services" && subDir.listFiles()?.isEmpty() == true) {
                    subDir.delete()
                }
            }
        }

        if (cleanedFiles == 0) {
            println("[Copilot Junk Cleaner] Skipped: No candidate junk build metadata found.")
            return@execute
        }

        val formattedSavings = LocaleUtils.formatBytes(savedBytes)
        println("[Copilot Junk Cleaner] Cleaned $cleanedFiles junk metadata files -> Saved $formattedSavings.")
    }
}

private val PROTECTED_ROOT_FILES = setOf(
    "androidmanifest.xml",
    "resources.arsc",
)

private val PROTECTED_EXTENSIONS = setOf(
    "dex", "arsc", "xml", "so",
)

private val PROTECTED_META_INF_FILES = setOf(
    "manifest.mf",
)

private val PROTECTED_META_INF_EXTENSIONS = setOf(
    "sf", "rsa", "dsa", "ec",
)

private val JUNK_ROOT_EXACT_NAMES = setOf(
    "DebugProbesKt.bin",
    "stamp-cert-sha256",
    "version-control-info.textproto",
    "kotlin-tooling-metadata.json",
)

private val JUNK_EXTENSIONS = setOf(
    "properties",
    "proto",
    "textproto",
    "version",
)

private val JUNK_META_INF_PREFIXES = listOf(
    "LICENSE",
    "NOTICE",
    "README",
    "DEPENDENCIES",
    "CHANGES",
    "AL2.0",
    "LGPL",
    "ASL2.0",
    "APACHE",
)

private fun isRootJunkFile(file: File): Boolean {
    if (!file.isFile) return false
    val name = file.name
    val lower = name.lowercase()

    if (lower in PROTECTED_ROOT_FILES) return false
    if (lower.startsWith("classes") && lower.endsWith(".dex")) return false
    val ext = file.extension.lowercase()
    if (ext in PROTECTED_EXTENSIONS) return false

    if (name in JUNK_ROOT_EXACT_NAMES) return true
    return ext in JUNK_EXTENSIONS
}

private fun isMetaInfJunkFile(file: File): Boolean {
    if (!file.isFile) return false
    val name = file.name
    val lower = name.lowercase()

    if (lower in PROTECTED_META_INF_FILES) return false
    val ext = file.extension.lowercase()
    if (ext in PROTECTED_META_INF_EXTENSIONS) return false

    // Check for build metadata or compiler leftovers
    if (name in JUNK_ROOT_EXACT_NAMES || ext in JUNK_EXTENSIONS) {
        return true
    }

    // Check for text license / notice / readme duplicates
    val baseName = name.substringBeforeLast('.').uppercase()
    return JUNK_META_INF_PREFIXES.any { prefix ->
        baseName == prefix ||
            baseName.startsWith("${prefix}_") ||
            baseName.startsWith("${prefix}-") ||
            baseName.startsWith("${prefix}.")
    }
}
