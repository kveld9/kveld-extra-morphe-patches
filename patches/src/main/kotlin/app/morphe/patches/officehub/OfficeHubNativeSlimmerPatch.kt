package app.morphe.patches.officehub

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File
import java.io.RandomAccessFile

@Suppress("unused")
val officeHubNativeSlimmerPatch = rawResourcePatch(
    name = "Copilot Companion Native Slimmer",
    description = "Strips optional companion native binaries (React Native, Hermes, SlimCV) via in-situ zeroing. WARNING: Hermes and React Native are load-bearing for React Native initialization - enabling this WILL crash Copilot React Native surfaces (Copilot chat host) with UnsatisfiedLinkError. The HockeyApp native exception handler (proven UnsatisfiedLinkError in OfficeApplication.onMAMCreate) and voice/dictation SDKs (libofficevoicesdk, libofficevoicetranscriptionsdk; proven dlopen FATAL on boot path) are load-bearing at startup and are therefore never stripped.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_OFFICEHUB)

    execute {
        var savedBytes = 0L
        val strippedLibs = mutableListOf<String>()

        for (abi in TARGET_ABIS) {
            val abiDir = get(abi)
            val (stripped, freed) = stripCompanionLibsInAbi(abiDir, abi)
            strippedLibs.addAll(stripped)
            savedBytes += freed
        }

        if (strippedLibs.isEmpty()) {
            println("[Copilot Companion Native Slimmer] Skipped: No candidate companion native libraries found.")
            return@execute
        }

        val formattedSavings = LocaleUtils.formatBytes(savedBytes)
        println("[Copilot Companion Native Slimmer] Stripped ${strippedLibs.size} companion binaries (${strippedLibs.joinToString(", ")}) -> Saved $formattedSavings.")
    }
}

private val TARGET_ABIS = listOf("lib/arm64-v8a", "lib/armeabi-v7a")

private val COMPANION_SO_NAMES = listOf(
    "libhermes.so",
    "libreactnative.so",
    "libSlimCV.so",
    "libhermestooling.so",
    "libfbjni.so",
)

// Standard 4-byte ELF magic header: 0x7F, 'E', 'L', 'F'
private val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())

private fun stripCompanionLibsInAbi(abiDir: File, abiPath: String): Pair<List<String>, Long> {
    if (!abiDir.isDirectory) return Pair(emptyList(), 0L)

    val stripped = mutableListOf<String>()
    var saved = 0L

    for (libName in COMPANION_SO_NAMES) {
        val libFile = File(abiDir, libName)
        val freed = zeroElfBinaryIfFingerprintMatches(libFile)
        if (freed > 0L) {
            saved += freed
            stripped.add("$abiPath/$libName")
        }
    }
    return Pair(stripped, saved)
}

private fun zeroElfBinaryIfFingerprintMatches(soFile: File): Long {
    if (!soFile.isFile || soFile.length() < ELF_MAGIC.size.toLong()) return 0L
    val originalSize = soFile.length()

    return try {
        RandomAccessFile(soFile, "rw").use { raf ->
            if (!isElfMagicHeaderValid(raf)) return@use 0L
            raf.setLength(0L)
            originalSize
        }
    } catch (_: Exception) {
        0L
    }
}

private fun isElfMagicHeaderValid(raf: RandomAccessFile): Boolean {
    val fileLength = raf.length()
    if (fileLength < ELF_MAGIC.size.toLong()) return false

    val header = ByteArray(ELF_MAGIC.size)
    raf.seek(0L)
    raf.readFully(header)
    return header.contentEquals(ELF_MAGIC)
}
