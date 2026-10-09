---
name: app-debloat
description: Architectural patterns and implementation guides for Android application asset, native binary, locale, density, and background sync debloating.
---

# Application Debloating & Asset Slimming Guidelines

## 1. Analysis Methodology

Modern Android applications bundle multi-megabyte payloads of unused native companion libraries, multi-language string tables, high-density graphics, promotional onboarding videos, editor assets, and background sync schedulers.

Debloating systematically removes these unused assets to minimize APK footprint, reduce RAM consumption, and eliminate background battery drain.

### A. Native Library Inventory (`lib/<abi>/*.so`)
Triage native binaries by inspecting APK directory contents:

```bash
unzip -l candidate_apks/<app>.apk "lib/*" | sort -k3 -n
```

Classify candidate binaries across structural categories:
1. **Companion AI & Machine Learning Engines**:
   - Vision & face tracking: `libimpress_api_jni.so`, `libAndroidPitayaCore.so`, `libAndroidPitayaProxy.so`, `libPitayaBdComponent.so`
   - On-device LLM & inference: `libbytennllm.so`, `libbytennllm-jni.so`, `libdex_df_gemini_nano.so`
   - Card scanners & FinTech OCR: `libBlinkCard.so`, `libdex_df_ccdc_impl_ocr.so`
2. **Bundled VPN Runtimes**:
   - WireGuard Go: `libwg-go.so`
3. **XR & ARCore Runtimes**:
   - OpenXR / ARCore: `libandroidx.xr.arcore.openxr.so`, `libandroidx.xr.runtime.openxr.so`, `libarcore_sdk_c.so`, `libarcore_sdk_jni.so`
4. **Crash Reporters & Profilers**:
   - Crash collectors: `libcrashlytics.so`, `libsentry.so`, `libbugly.so`, `libplcrashreporter.so`
   - Memory & CPU profilers: `libgwp-asan.so`, `libsimpleperf.so`, `libreschecker.so`, `libleaktracer.so`, `libperfa_arm64.so`
5. **Speech & Voice Recognition Engines**:
   - On-device speech recognition: `libspeechspg.so`, `libspeechsdk.so`, `libspeechengine.so`, `libspeechepg.so`

### B. Resource Inventory
Inventory APK assets and resources across primary size drivers:
1. **Localization Directories**:
   - Standard Android: `res/values-<locale>/` (e.g. `res/values-es/`, `res/values-fr/`)
   - Chromium DataPack v5: `assets/locales/<locale>.pak` (e.g. `en-US.pak`, `es.pak`, `pt-BR.pak`)
   - Custom string bundles: `assets/strings#lang_<codeTag>/` (e.g. TikTok language packs)
2. **Screen Densities & UI Modes**:
   - Densities: `res/drawable-mdpi`, `-hdpi`, `-xhdpi`, `-xxhdpi`, `-xxxhdpi`
   - Wear OS & TV UI modes: `res/drawable-watch`, `res/layout-watch`, `res/layout-television`
3. **Typography & Fonts**:
   - Non-Latin localized fonts: `NotoSansGreek.ttf`, `NotoSansHebrew.ttf`, `NotoSansKhmer.ttf`, `NotoSansThai.ttf` in `assets/fonts/`
   - Photo/video editor typography packs: `imgly_font_*.ttf` in `assets/fonts/`
4. **Onboarding Media & Tutorials**:
   - Embedded MP4 video files in `res/raw/` (e.g. Hevy onboarding workouts, tutorial clips)
5. **Editor Assets & Stickers**:
   - Overlay textures, stickers, frames: `imgly_text_design_*`, `imgly_overlay_*` in `res/`
6. **Ad Network Secondary Artifacts**:
   - Secondary ad DEX files: `assets/audience_network.dex`, `assets/audience_network/classes.dex`
   - Ad web templates: `assets/ad-viewer/`, `assets/template/`, `assets/iads/`
   - Ad network raster drawables: `applovin_*`, `mbridge_*`, `ironsource_*`, `fyber_*` in `res/`
7. **Compiler Junk & Metadata**:
   - Build metadata in APK root: `DebugProbesKt.bin`, `stamp-cert-sha256`, `version-control-info.textproto`, `kotlin-tooling-metadata.json`
   - Duplicate license and notice files in `META-INF/`

### C. Background Sync Inventory
Audit `AndroidManifest.xml` for persistent background wakeups:
1. **WorkManager & Job Schedulers**:
   - `androidx.work.impl.background.systemalarm.SystemAlarmService`
   - `androidx.work.impl.background.systemjob.SystemJobService`
   - `androidx.work.impl.foreground.SystemForegroundService`
2. **Boot & Lifecycle Receivers**:
   - Permissions: `android.permission.RECEIVE_BOOT_COMPLETED`, `android.permission.WAKE_LOCK`
   - Intent filters: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`
   - Constraint proxies: `ConstraintProxy$BatteryChargingProxy`, `ConstraintProxy$NetworkStateProxy`

### D. Keep-Everything-Runnable Rule
1. **Granular Axis Isolation**: Never bundle distinct debloat axes into a single monolithic patch. Create independent, opt-in patches for each axis so users configure what to strip:
   - Separate native trimmer (`default = false` or `default = true` if companion is unreferenced).
   - Separate locale trimmer with `stringOption` (`default = false`).
   - Separate density trimmer with `stringOption` (`default = false`).
   - Separate background sync purge (`default = false`).
   - Separate speech engine trimmer (`default = false` with functional impact warning).
2. **Preserve Application Invariants**:
   - Launcher icons (`@mipmap/ic_launcher`, round icons) must never be stripped during DPI slimming.
   - Base language fallback (`res/values/`) must never be stripped during locale slimming.
   - Density-independent assets (`nodpi`, `anydpi`) must never be stripped during density slimming.
   - Never break XML layout inflation by deleting resource IDs referenced in compiled code.

---

## 2. Implementation Patterns per Axis

### A. Native Library In-Situ Zeroing (`rawResourcePatch`)
Deleting a `.so` file from the unzipped directory structure can alter ZIP central directory offsets or trigger native loader failures. In-situ zeroing (`file.writeBytes(byteArrayOf())`) preserves the file entry in the ZIP header while freeing virtually 100% of the compressed and uncompressed space.

```kotlin
private val EMPTY_STUB_BYTES = byteArrayOf()

private val BLOAT_NATIVE_LIBS = listOf(
    "libimpress_api_jni.so",
    "libwg-go.so",
    "libandroidx.xr.arcore.openxr.so",
    "libarcore_sdk_c.so",
)

val exampleNativeSlimmerPatch = rawResourcePatch(
    name = "Native Bloat Slimmer",
    description = "Strips unused companion native binaries (Vision AI, WireGuard VPN, XR) to reduce APK size.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_<APP>)

    execute {
        var savedBytes = 0L
        val strippedLibs = mutableListOf<String>()
        val abis = listOf("lib/arm64-v8a", "lib/armeabi-v7a")

        abis.forEach { abi ->
            val libDir = get(abi)
            if (!libDir.exists() || !libDir.isDirectory) return@forEach

            BLOAT_NATIVE_LIBS.forEach { libName ->
                val libFile = get("$abi/$libName")
                if (libFile.exists() && libFile.isFile) {
                    val originalSize = libFile.length()
                    if (originalSize > 0) {
                        libFile.writeBytes(EMPTY_STUB_BYTES)
                        savedBytes += originalSize
                        strippedLibs.add("$abi/$libName")
                    }
                }
            }
        }

        val totalSavedMb = String.format(java.util.Locale.US, "%.2f", savedBytes.toDouble() / (1024 * 1024))
        println("[Native Bloat Slimmer] Stripped ${strippedLibs.size} bloat binaries (${strippedLibs.joinToString(", ")}) -> Saved $totalSavedMb MB")
    }
}
```

### B. Locale & Language Slimming

#### 1. Android Resource Table (`res/values-*/`)
Strip localized string tables while strictly protecting unquantified base fallbacks:
- Read user target languages via `stringOption(key = "locales", default = "en")`.
- Always add `en` and `en-us` to the retained set to guarantee fallback safety.
- Delete only language-qualified directories (`res/values-es/`, `res/values-fr/`).
- Never delete base `res/values/` containing primary layout definitions, colors, IDs, and default strings.

#### 2. Chromium DataPack v5 Locales (`assets/locales/*.pak`)
Chromium's native C++ bundle loader (`ui::ResourceBundle::LoadLocaleResources`) will crash with `Check failed: file_is_valid` if a `.pak` file is deleted or truncated to 0 bytes when the device system locale matches the missing file.
- **Substitution Strategy**: Overwrite unselected `.pak` files with the complete byte contents of `en-US.pak`. The native loader successfully parses the DataPack header and displays English text without crashing.
- **Gender Variants**: Overwrite gender variants (e.g. `_feminine`, `_masculine`) with the minimal valid 18-byte DataPack v5 empty header:
  ```kotlin
  private val EMPTY_DATAPACK_V5 = byteArrayOf(
      0x05, 0x00, 0x00, 0x00, // version 5
      0x01, 0x01,             // tables=1, encoding=1
      0x00, 0x00, 0x00, 0x00, // num_aliases=0, num_resources=0
      0x00, 0x00, 0x00, 0x00, // alias_count=0
      0x12, 0x00, 0x00, 0x00  // margin index offset (18)
  )
  ```

### C. Screen Density & UI Mode Trimming (`resourcePatch`)
Screen density slimming strips unselected `drawable-*` and `mipmap-*` directories (e.g. `mdpi`, `hdpi`, `xhdpi`) while preserving display integrity:

1. **Protect Manifest Launcher Icons**:
   Parse `AndroidManifest.xml` in read-only mode to extract `@mipmap/` and `@drawable/` icons registered on `<application>`, `<activity>`, and `<activity-alias>` tags. Never remove these entries from any density directory to prevent blurry launcher icons on non-standard device densities.

2. **Preserve Density-Independent Resources**:
   Never touch `drawable-nodpi/` or `drawable-anydpi/`. Vector drawables (`.xml`) placed in `anydpi` or `nodpi` scale dynamically across all screens.

3. **In-Situ Preservation of Single-Density Orphan Assets**:
   If an image asset only exists in `drawable-hdpi` and has no corresponding file in the target density (e.g. `xxhdpi`), do NOT delete it. Deleting it causes runtime `Resources.NotFoundException` crashes when inflated. Preserve orphan assets in-situ.

4. **UI Mode Qualifier Purging**:
   Remove non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`) across `drawable` and `layout` directories:
   ```kotlin
   val qualifiers = dir.name.split("-").drop(1).map { it.lowercase() }
   if (qualifiers.any { it in setOf("watch", "television", "car") } && !dir.name.startsWith("values")) {
       dir.deleteRecursively()
   }
   ```

### D. Asset Replacement & Root Junk Cleaner (`rawResourcePatch`)

1. **Heavy Video & Tutorial Stubbing**:
   Replace multi-megabyte MP4 tutorial videos in `res/raw/` with a valid 32-byte ISO MP4 container header:
   ```kotlin
   private val EMPTY_MP4_HEADER = byteArrayOf(
       0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D,
       0x00, 0x00, 0x02, 0x00, 0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
       0x61, 0x76, 0x63, 0x31, 0x6D, 0x70, 0x34, 0x31
   )
   ```

2. **Heavy Texture & Sticker Stubbing**:
   Replace unused photo editor stickers and overlay bitmaps with a 67-byte 1x1 transparent PNG:
   ```kotlin
   private val EMPTY_TRANSPARENT_PNG = byteArrayOf(
       0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
       0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06,
       0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(), 0x00, 0x00, 0x00, 0x0B,
       0x49, 0x44, 0x41, 0x54, 0x78, 0x9C.toByte(), 0x63, 0x60, 0x00, 0x02, 0x00, 0x00, 0x05, 0x00,
       0x01, 0xE9.toByte(), 0xFA.toByte(), 0xDC.toByte(), 0xD8.toByte(), 0x00, 0x00, 0x00,
       0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
   )
   ```

3. **Compiler Junk Cleaner**:
   Purge non-functional build metadata and properties files from the APK root while strictly protecting `assets`, `res`, `lib`, and `smali` trees:
   - Remove `*.properties`, `*.proto`, `*.version`, `DebugProbesKt.bin`.
   - Remove duplicate `LICENSE`, `NOTICE`, `README` text files from `META-INF/`.
   - Clean empty subdirectories in `META-INF/` and orphan `kotlin/` metadata directories.

### E. Background Sync & JobScheduler Purge (`resourcePatch`)
Eliminate battery-draining wakeups and periodic synchronizations:
1. Strip permission: `android.permission.RECEIVE_BOOT_COMPLETED`.
2. Disable broadcast receivers matching boot intent filters (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`).
3. Disable WorkManager services (`SystemAlarmService`, `SystemJobService`, `SystemForegroundService`).
4. Keep `android.permission.WAKE_LOCK` intact by default to prevent disrupting audio playback or active background downloads.

---

## 3. Verification

### A. Size Savings Reporting
Every debloat patch must calculate and report the exact amount of space saved in uncompressed bytes, formatted in KB or MB:

```kotlin
val savedMb = String.format(java.util.Locale.US, "%.2f", savedBytes.toDouble() / (1024 * 1024))
println("[<Patch Name>] Cleaned $count files -> Saved $savedMb MB")
```

### B. Smoke Launch Verification
Verify that the APK launches cleanly on the target device or runtime runner:
1. **Zero UnsatisfiedLinkError**: Verify that trimmed `.so` binaries were truly optional companion libraries and not hard-dependencies loaded by `System.loadLibrary()`.
2. **Zero Resources.NotFoundException**: Verify that XML layouts and activities inflate without missing drawable or string IDs.
3. **Zero UI Glitches**: Confirm launcher icons display sharply and fallback language strings render correctly when switching device system locales.

### C. Patcher Harness Gate
Execute the standard test runner to guarantee 100% build pass:

```bash
# Verify debloat patch execution against target APK
./gradlew runPatchTest -Papp=<app_id> -PallOptions=true

# Quiet execution for automated CI / evaluation
./gradlew runPatchTest -Papp=<app_id> --console=plain > build/patchtest-<app_id>.log 2>&1
```

Pass criteria:
- Failed patches: `0`
- Detected Exceptions: `0`
- Exit code: `0`
