# CapCut Patches Guide

Technical documentation and patch catalog for CapCut on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | CapCut |
| **Package Name** | `com.lemon.lvoverseas` |
| **Target Version** | `19.7.0` |
| **Target Package Format** | standalone APK |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `CapCut Telemetry Manifest Purge` | Neutralizes Google AppMeasurement event dispatchers and strips advertising identifiers. |
| **CapCut Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips 6 advertising/tracking permissions, disables measurement/analytics/ad components, prunes analytics discovery registrars (MLKit/messaging/crashlytics preserved), injects 8 opt-out metadata flags. |
| **Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected res/values-* tables and assets/locales/*.json bundles via locales option (default en). Base fallback and English always preserved. |
| **Screen Density Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected drawable/mipmap density assets and purges watch/television/car/vrheadset UI modes via density option (default xxhdpi). Launcher icons, nodpi/anydpi and single-density orphans preserved. |
| **Bypass Effects Region Restriction** | `bytecodePatch` | `true` (Enabled) | None | Spoofs device_platform to "windows" and resets deviceId in EffectConfiguration to bypass ByteDance Shark WAF effect loading blocks. |
| **Native Bloat Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | Zeroes AppLovin crash-reporter .so in-situ across arm64-v8a/armeabi-v7a; optional trimSpeechEngines toggle zeroes speech .so libs (breaks voice features). |

---

## 3. Deep Technical Breakdown

### A. Manifest Purge Layer (`resourcePatch`)

The resource patch executes declarative AST transformations on `AndroidManifest.xml` before DEX assembly:

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID` (Google Play Services Advertising ID)
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` (Privacy Sandbox Attribution API)
   - `android.permission.ACCESS_ADSERVICES_AD_ID` (Android Privacy Sandbox Ad ID)
   - `android.permission.ACCESS_ADSERVICES_TOPICS` (Privacy Sandbox Topics API)
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` (Play Install Referrer)
   - `com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE` (AppLovin AppHub Service)

2. **Component Disabling**:
   Disables analytics, tracking, measurement, and advertising components by setting `android:enabled="false"` and `android:exported="false"`:
   - **Providers**:
     - `com.applovin.sdk.AppLovinInitProvider`
     - `com.google.android.gms.ads.MobileAdsInitProvider`
     - `com.facebook.internal.FacebookInitProvider`
   - **Services**:
     - `com.google.android.gms.measurement.AppMeasurementService`
     - `com.google.android.gms.measurement.AppMeasurementJobService`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
     - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
     - `com.applovin.impl.adview.activity.FullscreenAdService`
   - **Receivers**:
     - `com.google.android.gms.measurement.AppMeasurementReceiver`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`
     - `com.appsflyer.SingleInstallBroadcastReceiver`
   - **Dynamic Ad Activities**:
     - Matches and disables activity elements containing `AppLovinFullscreen`, `AppLovinWebView`, `MaxDebugger`, `MaxCreativeDebugger`, or `VungleActivity`.

3. **Discovery Registrar Pruning**:
   - Scans `com.google.firebase.components.ComponentDiscoveryService` registrars and removes entries matching analytics, measurement, crashlytics, perf, remoteconfig, sessions, or abt.
   - Strictly preserves MLKit (`mlkit`), FCM push messaging (`messaging`), and Firebase Crashlytics runtime registrars (`crashlytics`).

4. **Opt-Out Metadata Injection**:
   Injects declarative configuration tags under `<application>` to disable telemetry collection:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `firebase_sessions_enabled` = `false`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

### B. Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes Google AppMeasurement event submission:

1. **Targeted Entrypoint Hooks**:
   - `Lcom/google/android/gms/measurement/AppMeasurement;->logEventInternal(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)V`: Injects `return-void` at instruction index 0.

2. **Hook Hygiene & Stability**:
   - Zero speculative hooks: targets only verified runtime entrypoints.
   - Preserves non-telemetry Firebase components and core video rendering pipelines without causing null reference exceptions.

### C. Resource and Binary Debloat Axes

1. **Locale Slimmer (`resourcePatch`)**:
   - **Axis 1A (Android Resource Table)**: Recursively removes unselected `res/values-*` directories matching unselected language qualifiers.
   - **Axis 1B (CapCut Asset Localization Bundles)**: Deletes unselected JSON locale bundles in `assets/locales/*.json`.
   - **Safe-Retained Lists**:
     - Base fallback string directory (`res/values`) and non-language qualifiers (e.g. `values-night`, `values-v31`) are never stripped.
     - English fallback assets (`en`, `en-US`, `en-*`, `en_*`) are strictly preserved.
     - User-selected locales configured via `locales` option (default `en`).

2. **Screen Density Slimmer (`resourcePatch`)**:
   - **Phase 1 (UI Mode Directory Purge)**: Deletes non-phone UI mode qualifiers across all resource directories (`watch`, `television`, `car`, `vrheadset`).
   - **Phase 2 (Density Trimming)**: Strips unselected densities across `drawable-*` and `mipmap-*` families.
   - **Safe-Retained Lists**:
     - Density-independent directories (`nodpi`, `anydpi`) and base qualifiers without density tokens (e.g. `drawable`, `drawable-v21`).
     - Launcher application icons (`ic_launcher`, `ic_launcher_round`, and icons declared in `AndroidManifest.xml`).
     - Single-density orphan assets (files that only exist in one density folder across the APK are guarded against deletion to avoid missing resource crashes).
     - Target density directory configured via `density` option (default `xxhdpi`).

3. **Native Bloat Slimmer (`rawResourcePatch`)**:
   - Zeroes target companion `.so` files in-situ (`writeBytes(byteArrayOf())`) within `lib/<abi>/` architectures (`arm64-v8a`, `armeabi-v7a`).
   - **Default Targets**: Always zeroes `libapplovin-native-crash-reporter.so`.
   - **Optional Toggle (`trimSpeechEngines`)**: When enabled (`default = false`), zeroes speech synthesis and recognition binaries (`libspeechengine.so`, `libspeechepg.so`, `libspeechsdk.so`). Voice effects, text-to-speech, and automatic captions will cease functioning when stripped.
   - **Safe-Retained Lists**: Core video processing, editing, decoding/encoding, and rendering native engines are untouched.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following risks before applying resource slimmer patches:

1. **Opt-in Status**:
   Telemetry and resource slimmer patches will be opt-in and disabled by default (`default = false`).
2. **Screen Density Slimmer**:
   Strips unselected density drawable and mipmap directories, and purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`). While launcher icons, nodpi/anydpi, and single-density orphans are guarded, stripping density resources may degrade visual assets, alter icon rendering, or cause layout clipping on devices whose display densities or display scaling settings differ from the preserved density.
3. **Locale Slimmer**:
   Recursively deletes unselected language-specific `values-*` directories. While base fallback strings (`res/values`) and English are retained, all stripped languages become unavailable. If the host system language is stripped, the application falls back to base or English strings.
4. **Mandatory Backup & Verification**:
   Users must retain a clean backup copy of the original CapCut APK and test the patched package thoroughly before replacing their active daily installation.
