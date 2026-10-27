package app.morphe.patches.capcut

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import java.io.File
import java.io.RandomAccessFile

private const val HASH_SIZE = 32

private val TARGET_SO_PATHS = listOf(
    "lib/arm64-v8a/libenvguard.so",
    "lib/armeabi-v7a/libenvguard.so",
)

// Stock CapCut 19.9.0 SHA-256 signing certificate hash (raw 32 bytes)
private val STOCK_CERT_HASH = byteArrayOf(
    0x6f.toByte(), 0x33.toByte(), 0xfd.toByte(), 0x44.toByte(),
    0x6f.toByte(), 0x6e.toByte(), 0x83.toByte(), 0xa7.toByte(),
    0x67.toByte(), 0xd4.toByte(), 0xff.toByte(), 0xf9.toByte(),
    0xc1.toByte(), 0x9c.toByte(), 0xec.toByte(), 0xf7.toByte(),
    0x60.toByte(), 0x59.toByte(), 0x81.toByte(), 0x4e.toByte(),
    0xa6.toByte(), 0xa7.toByte(), 0xea.toByte(), 0x71.toByte(),
    0xdf.toByte(), 0x1d.toByte(), 0x0d.toByte(), 0xef.toByte(),
    0x2d.toByte(), 0x75.toByte(), 0xdb.toByte(), 0x31.toByte(),
)

// Patcher output signing certificate SHA-256 hash (raw 32 bytes)
private val TARGET_CERT_HASH = byteArrayOf(
    0x91.toByte(), 0xf0.toByte(), 0x03.toByte(), 0xf0.toByte(),
    0xa6.toByte(), 0xfe.toByte(), 0xb3.toByte(), 0x90.toByte(),
    0xd3.toByte(), 0xc8.toByte(), 0xfe.toByte(), 0xc1.toByte(),
    0x28.toByte(), 0xc1.toByte(), 0xea.toByte(), 0xa4.toByte(),
    0xf8.toByte(), 0x21.toByte(), 0x8c.toByte(), 0x25.toByte(),
    0x7c.toByte(), 0x53.toByte(), 0xb2.toByte(), 0xa8.toByte(),
    0x8d.toByte(), 0x23.toByte(), 0x1f.toByte(), 0xc4.toByte(),
    0xd8.toByte(), 0xeb.toByte(), 0xd1.toByte(), 0x64.toByte(),
)

@Suppress("unused")
val capcutEnvGuardCertPatch = rawResourcePatch(
    name = "EnvGuard Certificate Alignment",
    description = "Aligns embedded security certificates to allow re-signed builds to pass integrity verification cleanly.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    execute {
        val presentEntries = listApkEntries("lib/").toSet()
        var filesPatched = 0
        var bytesReplaced = 0

        for (targetPath in TARGET_SO_PATHS) {
            val file = resolveTargetFile(this, targetPath, presentEntries) ?: continue
            val occurrences = patchFile(file, targetPath)
            filesPatched++
            bytesReplaced += occurrences * HASH_SIZE
        }

        if (filesPatched > 0) {
            println("[EnvGuard Certificate Alignment] Patched $filesPatched files ($bytesReplaced bytes replaced) -> certificate hash aligned.")
        }
    }
}

private fun resolveTargetFile(
    context: ResourcePatchContext,
    targetPath: String,
    presentEntries: Set<String>,
): File? {
    if (targetPath !in presentEntries) {
        println("[EnvGuard Certificate Alignment] Skipped: $targetPath is absent.")
        return null
    }

    val file = context.get(targetPath)
    if (!file.exists() || !file.isFile) {
        println("[EnvGuard Certificate Alignment] Skipped: $targetPath is absent.")
        return null
    }

    return file
}

private fun patchFile(file: File, targetPath: String): Int {
    val fileBytes = file.readBytes()
    val offsets = findSequenceOffsets(fileBytes, STOCK_CERT_HASH)
    if (offsets.isEmpty()) {
        error("[EnvGuard Certificate Alignment] Stock certificate hash not found in $targetPath")
    }

    RandomAccessFile(file, "rw").use { raf ->
        for (offset in offsets) {
            patchOffset(raf, offset, targetPath)
        }
    }

    return offsets.size
}

private fun patchOffset(
    raf: RandomAccessFile,
    offset: Long,
    targetPath: String,
) {
    val fileLength = raf.length()
    if (offset + HASH_SIZE > fileLength) {
        error("[EnvGuard Certificate Alignment] Offset 0x${offset.toString(16)} out of bounds in $targetPath (file length: $fileLength)")
    }

    raf.seek(offset)
    val currentBytes = ByteArray(HASH_SIZE)
    raf.readFully(currentBytes)
    if (!currentBytes.contentEquals(STOCK_CERT_HASH)) {
        error(
            "[EnvGuard Certificate Alignment] Stock certificate hash mismatch at offset 0x${offset.toString(16)} in $targetPath",
        )
    }

    raf.seek(offset)
    raf.write(TARGET_CERT_HASH)
}

private fun matchesAt(data: ByteArray, pattern: ByteArray, offset: Int): Boolean {
    for (i in pattern.indices) {
        if (data[offset + i] != pattern[i]) return false
    }
    return true
}

private fun findSequenceOffsets(data: ByteArray, pattern: ByteArray): List<Long> {
    if (pattern.isEmpty() || data.size < pattern.size) return emptyList()

    val matches = mutableListOf<Long>()
    val limit = data.size - pattern.size
    for (i in 0..limit) {
        if (matchesAt(data, pattern, i)) {
            matches.add(i.toLong())
        }
    }
    return matches
}
