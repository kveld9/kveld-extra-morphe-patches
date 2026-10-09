package app.morphe.patches.capcut

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import java.io.File
import java.util.Locale

private val CRASH_REPORTER_LIBS = setOf(
    "libapplovin-native-crash-reporter.so",
)

private val SPEECH_ENGINE_LIBS = setOf(
    "libspeechengine.so",
    "libspeechepg.so",
    "libspeechsdk.so",
)

private val EMPTY_STUB_BYTES = byteArrayOf()

@Suppress("unused")
val capcutNativeSlimmerPatch = rawResourcePatch(
    name = "Native Bloat Slimmer",
    description = "Strips non-essential companion native libraries by zeroing bytes in-situ. Always zeroes AppLovin ad crash reporter. WARNING: Stripping speech recognition and synthesis engines (libspeech*.so) disables voice recognition, voiceover captions, and speech-to-text features.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_CAPCUT)

    val trimSpeechEngines by booleanOption(
        key = "trimSpeechEngines",
        default = false,
        title = "Trim Speech Engines (Breaks Voice Features)",
        description = "WARNING: Stripping speech recognition and synthesis engines (libspeechengine.so, libspeechepg.so, libspeechsdk.so) breaks voice effects, text-to-speech, and automatic captions.",
        required = false,
    )

    execute {
        val apkRoot = try {
            get("AndroidManifest.xml").parentFile ?: get(".")
        } catch (_: Throwable) {
            get(".")
        }

        val libDir = File(apkRoot, "lib")
        if (!libDir.exists() || !libDir.isDirectory) {
            println("[CapCut Native Slimmer] Skipped: lib directory not found.")
            return@execute
        }

        val targetLibs = mutableSetOf<String>()
        targetLibs.addAll(CRASH_REPORTER_LIBS)
        if (trimSpeechEngines ?: false) {
            targetLibs.addAll(SPEECH_ENGINE_LIBS)
        }

        val candidateFiles = libDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("so", ignoreCase = true) }
            .filter { it.name.lowercase() in targetLibs }
            .toList()

        if (candidateFiles.isEmpty()) {
            println("[CapCut Native Slimmer] No candidate native libraries found in lib/ - skipping.")
            return@execute
        }

        var savedBytes = 0L
        val strippedLibs = mutableListOf<String>()

        for (file in candidateFiles) {
            val originalSize = file.length()
            if (originalSize > 0) {
                file.writeBytes(EMPTY_STUB_BYTES)
                savedBytes += originalSize
                strippedLibs.add(file.relativeTo(apkRoot).path.replace('\\', '/'))
            }
        }

        if (strippedLibs.isEmpty()) {
            println("[CapCut Native Slimmer] Target native libraries were already zeroed.")
            return@execute
        }

        val totalSavedMb = String.format(Locale.US, "%.2f", savedBytes.toDouble() / (1024 * 1024))
        println("[CapCut Native Slimmer] Stripped ${strippedLibs.size} companion native libraries (${strippedLibs.joinToString(", ")}) -> Saved $totalSavedMb MB")
    }
}
