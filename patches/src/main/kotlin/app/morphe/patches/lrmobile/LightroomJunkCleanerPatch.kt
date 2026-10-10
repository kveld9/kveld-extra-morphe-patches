package app.morphe.patches.lrmobile

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File

@Suppress("unused")
val lightroomJunkCleanerPatch = rawResourcePatch(
    name = "Lightroom Junk Cleaner",
    description = "Purges non-functional build metadata, compiler properties, and duplicate license files from the APK root and META-INF.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_LRMOBILE)

    execute {
        val rootDir = get("").takeIf { it.exists() && it.isDirectory }
            ?: get("AndroidManifest.xml").parentFile
            ?: File(".")

        if (!rootDir.exists() || !rootDir.isDirectory) {
            println("[Lightroom Junk Cleaner] Skipped: Root directory not found.")
            return@execute
        }

        var prunedCount = 0
        var savedBytes = 0L

        // 1. Purge non-functional build metadata and properties from APK root only
        val rootFiles = rootDir.listFiles { file -> file.isFile } ?: emptyArray()
        for (file in rootFiles) {
            if (isProtectedRootFile(file)) continue

            if (isTargetRootJunkFile(file)) {
                val size = file.length()
                if (file.delete()) {
                    prunedCount++
                    savedBytes += size
                }
            }
        }

        // 2. Purge target metadata from META-INF
        val metaInfDir = get("META-INF")
        if (metaInfDir.exists() && metaInfDir.isDirectory) {
            val vcsInfo = get("META-INF/version-control-info.textproto")
            if (vcsInfo.exists() && vcsInfo.isFile) {
                val size = vcsInfo.length()
                if (vcsInfo.delete()) {
                    prunedCount++
                    savedBytes += size
                }
            }

            val verificationProps = get("META-INF/optimize/verification.properties")
            if (verificationProps.exists() && verificationProps.isFile) {
                val size = verificationProps.length()
                if (verificationProps.delete()) {
                    prunedCount++
                    savedBytes += size
                }
            }

            // Prune duplicate LICENSE.txt files in META-INF subdirectories
            val subDirs = metaInfDir.listFiles { dir -> dir.isDirectory } ?: emptyArray()
            for (subDir in subDirs) {
                val candidateFiles = subDir.walkTopDown().filter { file ->
                    file.isFile && file.name.equals("LICENSE.txt", ignoreCase = true)
                }
                for (licenseFile in candidateFiles) {
                    if (isProtectedSignatureFile(licenseFile)) continue
                    val size = licenseFile.length()
                    if (licenseFile.delete()) {
                        prunedCount++
                        savedBytes += size
                    }
                }
            }
        }

        val formattedSavings = LocaleUtils.formatBytes(savedBytes)
        println("[Lightroom Junk Cleaner] Pruned $prunedCount junk files -> Saved $formattedSavings.")
    }
}

private val PROTECTED_ROOT_FILES = setOf(
    "AndroidManifest.xml",
    "resources.arsc",
)

private val PROTECTED_DEX_REGEX = Regex("^classes\\d*\\.dex$")

private fun isProtectedRootFile(file: File): Boolean {
    val name = file.name
    if (name in PROTECTED_ROOT_FILES) return true
    if (PROTECTED_DEX_REGEX.matches(name)) return true
    return false
}

private fun isTargetRootJunkFile(file: File): Boolean {
    val name = file.name
    if (name == "DebugProbesKt.bin") return true
    if (name.endsWith(".properties", ignoreCase = true)) return true
    if (name.endsWith(".proto", ignoreCase = true)) return true
    return false
}

private fun isProtectedSignatureFile(file: File): Boolean {
    val name = file.name.uppercase()
    return name.endsWith(".SF") ||
        name.endsWith(".RSA") ||
        name.endsWith(".MF") ||
        name.endsWith(".DSA") ||
        name.endsWith(".EC")
}
