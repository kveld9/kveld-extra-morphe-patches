# Moovit Patches Guide

Technical documentation and patch catalog for Moovit on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Moovit |
| **Package Name** | `com.tranzmate` |
| **Target Version** | `5.201.1.1809` |
| **Target Package Format** | APKM (Split APK Bundle) |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Moovit Telemetry Manifest Purge` | Disables analytics and tracking services, providers, and receivers, and strips advertising permissions. |
| **Fix Google Maps** | `bytecodePatch` | `true` (Enabled) | None | Restores Google Maps rendering by spoofing the original package signature to Google Play Services. |
| **Moovit Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips advertising and tracking permissions, disables analytics services, providers, and receivers, and injects opt-out metadata in AndroidManifest.xml. |
| **Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected language string tables and resources from base APK. Base fallback and English are always preserved. |
| **Screen Density Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Strips unselected screen density assets and purges non-phone UI mode qualifiers. Launcher icons, nodpi/anydpi, and single-density orphans are always preserved. |

---

## 3. Deep Technical Breakdown

### A. Layer 1: Manifest Purge Layer (`resourcePatch`)

The resource patch executes declarative AST transformations on `AndroidManifest.xml` before DEX assembly:

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID` (Google Play Services Advertising ID)
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` (Privacy Sandbox Attribution API)
   - `android.permission.ACCESS_ADSERVICES_AD_ID` (Android Privacy Sandbox Ad ID)
   - `android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE` (Privacy Sandbox Custom Audiences)
   - `android.permission.ACCESS_ADSERVICES_TOPICS` (Privacy Sandbox Topics API)
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` (Play Install Referrer)

2. **Component Disabling**:
   Disables analytics, tracking, measurement, and advertising components by setting `android:enabled="false"` and `android:exported="false"`:
   - **Providers**:
     - `com.facebook.FacebookContentProvider`
     - `com.fairtiq.sdk.internal.telemetry.processTime.StartupTimeProvider`
     - `com.facebook.ads.AudienceNetworkContentProvider`
     - `com.facebook.internal.FacebookInitProvider`
     - `com.vungle.ads.VungleProvider`
   - **Services**:
     - `com.fairtiq.sdk.internal.services.tracking.TrackingServiceImpl`
     - `com.google.android.gms.measurement.AppMeasurementService`
     - `com.google.android.gms.measurement.AppMeasurementJobService`
     - `com.google.firebase.sessions.SessionLifecycleService`
     - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
   - **Receivers**:
     - `com.usebutton.sdk.internal.receivers.LocaleChangedReceiver`
     - `com.usebutton.sdk.internal.receivers.InstallNotificationReceiver`
     - `com.google.android.gms.measurement.AppMeasurementReceiver`
     - `com.facebook.CurrentAccessTokenExpirationBroadcastReceiver`
     - `com.facebook.AuthenticationTokenManager$CurrentAuthenticationTokenChangedBroadcastReceiver`
     - `com.braze.BrazeFlushPushDeliveryReceiver`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`

3. **Startup Initializer Removal**:
   Scans `androidx.startup.InitializationProvider` and purges child `<meta-data>` entries referencing Button SDK startup initializers (`ButtonSdkInitializer`).

4. **Component Discovery Registrars**:
   Dependency injection registrars under `com.google.firebase.components.ComponentDiscoveryService` are intentionally preserved (0 removed). The dependency injection graph in Moovit is coupled; suppression is achieved safely via opt-out metadata flags and disabled service/provider components.

5. **Opt-Out Metadata Injection**:
   Injects declarative configuration tags under `<application>` to disable SDK telemetry collection across initialization sequences:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `firebase_sessions_enabled` = `false`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

### B. Layer 2: Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The bytecode layer (`Block Telemetry & Trackers`) explicitly neutralizes 0 dispatch methods:
- There is no concrete analytics dispatch class in base DEX for Moovit.
- Manifest purge and declarative opt-out flags carry the entirety of tracker blocking.
- Diagnostic telemetry emits standard log lines reporting 0 neutralized bytecode methods.

### C. Google Maps Signature Spoofing (`bytecodePatch` + Companion Extension)

When Moovit is re-packaged or signed with custom keys, Google Play Services rejects Google Maps SDK authentication due to certificate mismatch with the registered API key:
- **Hook Point**: Injects an initialization call to `MoovitHelper.init()` in `MoovitApplication.onCreate`.
- **Companion Extension Payload**: Bundles `MoovitHelper.java` via `extensions/extension.mpe`.
- **Runtime Hook**:
  - Uses `HiddenApiBypass` to bypass hidden API restrictions on Android P+.
  - Dynamically proxies `ActivityThread.sPackageManager` and `ServiceManager.sCache["package"]`.
  - Intercepts `getPackageInfo` and `getPackageInfoAsUser` calls for `com.tranzmate` to return the official Tranzmate signing certificate to Google Play Services.
  - Guarantees full map tile loading, geocoding, and routing overlays without modifying host API keys.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following risks before applying resource slimmer patches:

1. **Opt-in Status**:
   Both `Locale Slimmer` (`moovitLocaleSlimmerPatch`) and `Screen Density Slimmer` (`moovitDpiSlimmerPatch`) are opt-in and disabled by default (`default = false`). `Moovit Telemetry Manifest Purge` is also opt-in (`default = false`), though chained as a dependency under `Block Telemetry & Trackers`.
2. **Screen Density Slimmer (`Screen Density Slimmer`)**:
   Strips unselected density drawable and mipmap directories, and purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`). While launcher icons, nodpi/anydpi, and single-density orphans are guarded, stripping density resources may degrade visual assets, alter icon rendering, or cause layout clipping on devices whose display densities or display scaling settings differ from the preserved density.
3. **Locale Slimmer (`Locale Slimmer`)**:
   Recursively deletes unselected language-specific `values-*` directories. While base fallback strings (`res/values`) and English are retained, all stripped languages become unavailable. If the host system language is stripped, the application falls back to base or English strings.
4. **Mandatory Backup & Verification**:
   Users must retain a clean backup copy of the original Moovit APK/APKM bundle and test the patched package thoroughly before replacing their active daily installation.
