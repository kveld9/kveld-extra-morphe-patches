package app.morphe.patches.powerpoint

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.LocaleUtils
import java.io.File
import java.io.RandomAccessFile

@Suppress("unused")
val powerPointCompanionSoSlimmerPatch = rawResourcePatch(
    name = "PowerPoint Companion Native Slimmer",
    description = "Strips optional companion native binaries (React Native and Hermes JavaScript runtime stack) via in-situ zeroing. WARNING: stripped libraries are load-bearing for React Native initialization - enabling this WILL crash the app with UnsatisfiedLinkError when Copilot or other React Native surfaces start, not merely hide those features.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_POWERPOINT)

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
            println("[PowerPoint Companion Native Slimmer] Skipped: No candidate companion native libraries found.")
            return@execute
        }

        val formattedSavings = LocaleUtils.formatBytes(savedBytes)
        println("[PowerPoint Companion Native Slimmer] Stripped ${strippedLibs.size} companion binaries (${strippedLibs.joinToString(", ")}) -> Saved $formattedSavings.")
    }
}

private val TARGET_ABIS = listOf("lib/arm64-v8a", "lib/armeabi-v7a")

private val COMPANION_SO_NAMES = listOf(
    "libhermes.so",
    "libreactnative.so",
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
    if (!soFile.isFile || soFile.length() < ELF_MAGIC.size) return 0L
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
    if (ELF_MAGIC.size.toLong() > fileLength) return false

    val header = ByteArray(ELF_MAGIC.size)
    raf.seek(0L)
    raf.readFully(header)
    return header.contentEquals(ELF_MAGIC)
}
