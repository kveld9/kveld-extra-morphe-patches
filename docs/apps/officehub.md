# Copilot Patches Guide

Technical documentation and patch catalog for Microsoft Copilot on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Copilot |
| **Package Name** | `com.microsoft.office.officehubrow` |
| **Target Version** | `16.0.20527.20022` |
| **Target Package Format** | APKM (Split APK Bundle) |
| **Authoritative Source** | [APKMirror](https://www.apkmirror.com/apk/microsoft-corporation/microsoft-copilot/microsoft-copilot-16-0-20527-20022-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Options | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Copilot Telemetry Manifest Purge` | `Custom Blocked Hosts` | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement platform identifiers (AIFA, AppSetId), rewrites telemetry endpoints to 0.0.0.0, disables cross-sell, Floodgate, HockeyApp and DataTransport components, and strips advertising permissions. |
| **Copilot Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | None | Strips advertising and tracking permissions, disables DataTransport, cross-sell, and HockeyApp components, and injects opt-out metadata in AndroidManifest.xml. |
| **Copilot DPI Slimmer** | `resourcePatch` | `false` (Opt-in) | None | `Target screen density` | Strips drawables for unselected screen densities from Copilot base APK while preserving launcher icons and single-density assets. WARNING: Displays matching stripped densities will scale preserved assets. |
| **Copilot Companion Native Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | None | Strips optional companion native binaries (React Native, Hermes, voice/dictation SDKs, SlimCV) via in-situ zeroing. WARNING: Hermes and React Native are load-bearing for React Native initialization - enabling this WILL crash Copilot React Native surfaces (Copilot chat host) with UnsatisfiedLinkError; speech stripping breaks voice typing and dictation features. The HockeyApp native exception handler is load-bearing at startup (proven UnsatisfiedLinkError in OfficeApplication.onMAMCreate) and is therefore never stripped. |
| **Copilot Junk Cleaner** | `rawResourcePatch` | `false` (Opt-in) | None | None | Purges non-functional build metadata, properties, proto descriptors, and duplicate license notices from APK root and META-INF while strictly protecting runtime assets and signatures. |

---

## 3. Deep Technical Breakdown

### A. Manifest Component Inventory

Static analysis of the target manifest (`AndroidManifest.xml`) establishes the following component architecture:

1. **Shared User ID**:
   - `sharedUserId="com.microsoft.office"`: Declared at the manifest root, coupling the package with other Microsoft Office suite installations unless stripped or aligned.

2. **Telemetry, Analytics & Background Transports**:
   - **Google DataTransport**:
     - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`
   - **Firebase Component Registrars**:
     - Firebase messaging, installations, and component registrars declared under `TransportRegistrar`.
   - **HockeyApp Diagnostics**:
     - `com.microsoft.office.hockeyapp.activities.HockeyWebViewActivity`
   - **Cross-Sell & Dynamic UX**:
     - `com.microsoft.android.crosssell.CrossSellAgentMarker`
     - `com.microsoft.android.crosssell.CrossSellReceiver`
     - `com.microsoft.android.crosssell.PackageStateReceiver`
     - `com.microsoft.android.crosssell.SelfReplacementReceiver`
     - `com.microsoft.android.crosssell.activities.ExcelCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.PdfCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.PowerpointCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.WordCrossSellHandlerActivity`
     - `com.microsoft.office.floodgate.launcher.FloodgateDynamicUxActivity`

3. **Preserved MLKit Components**:
   - MLKit registrars (`BarcodeRegistrar`, `ThinLabelRegistrar`, `TextRegistrar`, `VisionCommonRegistrar`, `CommonComponentRegistrar`) alongside `MlKitComponentDiscoveryService` and `MlKitInitProvider` are load-bearing for document scanning, OCR, and visual input, and must be strictly preserved across manifest purging.

### B. Manifest Purge Layer (`resourcePatch`)

The resource patch executes AST modifications on `AndroidManifest.xml` before bytecode assembly:

1. **Permission Stripping (2 Permissions)**:
   - `com.google.android.gms.permission.AD_ID` (Google Advertising ID)
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` (Play Install Referrer)

2. **Component Disabling (13 Components)**:
   Disables tracking, crash-reporting, and cross-sell components by setting `android:enabled="false"` and `android:exported="false"`:
   - **Google DataTransport**: `TransportBackendDiscovery`, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`
   - **HockeyApp**: `HockeyWebViewActivity`
   - **Microsoft Cross-Sell & Campaign Targeting**: `CrossSellAgentMarker`, `CrossSellReceiver`, `PackageStateReceiver`, `SelfReplacementReceiver`, `ExcelCrossSellHandlerActivity`, `PdfCrossSellHandlerActivity`, `PowerpointCrossSellHandlerActivity`, `WordCrossSellHandlerActivity`
   - **Floodgate**: `FloodgateDynamicUxActivity`

3. **Component Discovery Registrar Pruning**:
   Removes component discovery `<meta-data>` entries containing `TransportRegistrar`, `datatransport`, `analytics`, `measurement`, or `crashlytics` (1 removed; MLKit registrars matching `mlkit` are strictly preserved).

4. **Opt-Out Metadata Injection (7 Metadata Flags)**:
   Injects declarative configuration tags under `<application>`:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

### C. Bytecode Telemetry Dispatcher Neutralization (`bytecodePatch`)

Neutralizes Microsoft OneDS/Aria telemetry and ad measurement pipelines via 14 targeted Dalvik bytecode hooks:

1. **LifecycleHandler Hooks (7 Methods)**:
   Injects `return-void` at instruction index 0 in `Lcom/microsoft/applications/telemetry/core/LifecycleHandler;`:
   - `onActivityCreated(Activity, Bundle)V`
   - `onActivityStarted(Activity)V`
   - `onActivityResumed(Activity)V`
   - `onActivityPaused(Activity)V`
   - `onActivityStopped(Activity)V`
   - `onActivitySaveInstanceState(Activity, Bundle)V`
   - `onActivityDestroyed(Activity)V`

2. **Aggregated Metric & Failure Dispatcher Hooks (3 Methods)**:
   - `Lcom/microsoft/applications/telemetry/AggregatedMetric$SendAggregationTimerTask;->run()V`: Injects `return-void`.
   - `Lcom/microsoft/applications/telemetry/core/v;->logFailure(5 args)V`: Injects `return-void` (moved from `e0` in build train 16.0.20527.20022).
   - `Lcom/microsoft/applications/telemetry/core/v;->logFailure(3 args)V`: Injects `return-void` (moved from `e0` in build train 16.0.20527.20022).

3. **Power & Hardware Receivers (2 Methods)**:
   - `Lcom/microsoft/applications/telemetry/pal/hardware/HardwareInformationReceiver;->onMAMReceive(Context, Intent)V`: Injects `return-void`.
   - `Lcom/microsoft/unified/telemetry/mutsdk/PowerInfoReceiver;->onMAMReceive(Context, Intent)V`: Injects `return-void`.

4. **Ad Measurement Platform Identifiers (Empty String Nullification)**:
   Hooks identifier getters in `Lcom/microsoft/office/adsmobile_admeasurementpartner/admeasurement/AdMeasurementPlatformData;`:
   - `getAIFA()Ljava/lang/String;`: Clears try blocks and returns empty string `""`.
   - `getAppSetId()Ljava/lang/String;`: Clears try blocks and returns empty string `""`.

### D. DEX Hosts Rewrite Layer (Second-Layer Defense)

Rewrites `const-string` and `const-string/jumbo` hostname and URL literals matching telemetry endpoints to `0.0.0.0` across candidate classes in telemetry package prefixes:

1. **Candidate Scoping**:
   Scanned classes are strictly restricted to Microsoft telemetry packages (`core`, `mutsdk`, `asyncdatapointreporting`, `hockeyapp`, `crosssell`, `admeasurementpartner`) and Google/Firebase telemetry packages (`datatransport`, `measurement`, `analytics`, `crashlytics`).

2. **Default Blocked Hosts (11 Endpoints)**:
   - Microsoft OneDS / Aria / Vortex: `pipe.aria.microsoft.com`, `mobile.pipe.aria.microsoft.com`, `browser.pipe.aria.microsoft.com`, `vortex.data.microsoft.com`, `web.vortex.data.microsoft.com`, `telemetry.microsoft.com`, `watson.telemetry.microsoft.com`, `onecollector.cloudapp.net`.
   - Google / Firebase: `app-measurement.com`, `firebaselogging-pa.googleapis.com`, `crashlyticsreports-pa.googleapis.com`.

3. **Exclusions & Config**:
   - Reserved host exclusions: `localhost`, `localhost6`, `localhost.localdomain`, `0.0.0.0`, `127.0.0.1`, `::1`.
   - Optional `custom-blocked-hosts` string option allows user-supplied host rules.
   - Device verification: 75-second foreground tcpdump on physical device confirmed 0 WAN packets from the application (only LAN broadcast/SSDP chatter).

### E. Copilot DPI Slimmer (`resourcePatch`)

Prunes drawables and mipmaps for unselected screen densities to reduce APK size:

1. **Target Density Selection**:
   - Preserves drawables matching configured `targetDpi` (default `xxhdpi`).
   - Prunes unselected densities across `ldpi`, `mdpi`, `tvdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`.

2. **Asset Protection & Single-Density Rules**:
   - Strictly preserves launcher and app icons (`ic_officehub`, `ic_copilot`, `ic_office`, `ic_launcher*`, plus manifest icon attributes).
   - Preserves `nodpi`, `anydpi`, non-DPI drawable directories, and single-density orphan assets (assets with no counterpart in the target density).
   - Purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`).

### F. Companion Native Slimmer & Junk Cleaner (`rawResourcePatch`)

1. **Copilot Companion Native Slimmer**:
   - Zeroes unneeded companion native binaries in `lib/arm64-v8a` and `lib/armeabi-v7a` via in-situ ELF zeroing (`raf.setLength(0L)`):
     - React Native & Hermes stack: `libhermes.so`, `libreactnative.so`, `libfbjni.so`, `libhermestooling.so`.
     - Speech & vision SDKs: `libofficevoicesdk.so`, `libofficevoicetranscriptionsdk.so`, `libSlimCV.so`.
   - **Load-Bearing Exclusions**: `libhockey_exception_handler.so` is proven startup load-bearing (startup crash with `UnsatisfiedLinkError` in `OfficeApplication.onMAMCreate` if zeroed) and is permanently protected from stripping.

2. **Copilot Junk Cleaner**:
   - Purges non-functional root junk metadata: `DebugProbesKt.bin`, `stamp-cert-sha256`, `version-control-info.textproto`, `kotlin-tooling-metadata.json`, and `.properties`, `.proto`, `.textproto`, `.version` files.
   - Purges `META-INF` duplicate license and notice files (`LICENSE*`, `NOTICE*`, `README*`, `DEPENDENCIES*`, `AL2.0*`, `LGPL*`, `ASL2.0*`, `APACHE*`).
   - Strictly protects APK signatures (`.SF`, `.RSA`, `.DSA`, `.EC`), `MANIFEST.MF`, `META-INF/services/`, and DEX/ARSC assets.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following operational constraints before applying patches:

1. **Opt-in Status for Resource Slimmers**:
   Resource and asset slimmers (`Copilot DPI Slimmer`, `Copilot Companion Native Slimmer`, `Copilot Junk Cleaner`) and `Copilot Telemetry Manifest Purge` are opt-in and disabled by default (`default = false`). `Block Telemetry & Trackers` is enabled by default (`default = true`).

2. **DPI Fallback Consequences**:
   Pruning screen densities removes pre-rendered drawables for unselected densities. On devices whose screen density is stripped, Android scales remaining assets, which may introduce minor scaling artifacts.

3. **Companion Native Slimmer Crash Hazard & Feature Loss**:
   - Hermes and React Native binaries (`libhermes.so`, `libreactnative.so`, `libfbjni.so`, `libhermestooling.so`) are load-bearing for Copilot React Native surfaces. Enabling `Copilot Companion Native Slimmer` WILL crash the application with `UnsatisfiedLinkError` when Copilot chat surfaces are initialized.
   - Speech SDK binaries (`libofficevoicesdk.so`, `libofficevoicetranscriptionsdk.so`) are required for speech processing; stripping them disables voice typing and dictation features.
   - The native HockeyApp exception handler (`libhockey_exception_handler.so`) is verified startup load-bearing (`OfficeApplication.onMAMCreate`) and is intentionally never stripped to prevent startup crashes.

4. **Mandatory Backup & Verification**:
   Users must maintain a clean backup of the original APKM bundle and verify runtime stability before replacing existing installations. An all-options test build was verified on physical hardware (Redmi Note 5, arm64, Android 14 / SDK 34) launching successfully (PID alive, 0 FATAL crashes), with a 75-second foreground tcpdump showing zero application WAN traffic. Patches do not provide or claim local entitlement unlocking for cloud-backed Copilot subscriptions.
