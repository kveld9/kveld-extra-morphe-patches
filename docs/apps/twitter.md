# X (Twitter) Patches Guide

Technical documentation and patch catalog for X (formerly Twitter) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | X (Twitter) |
| **Package Name** | `com.twitter.android` |
| **Target Version** | `12.33.0-prod.01` |
| **Target Package Format** | APKM (Split APK Bundle) |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | [APKMirror](https://www.apkmirror.com/apk/x-corp/twitter/x-12-33-0-prod-01-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `X Telemetry Manifest Purge` | Neutralizes Google AppMeasurement event dispatchers, and strips advertising identifiers. |
| **X Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips tracking and advertising permissions, disables measurement services and receivers, and injects opt-out metadata in `AndroidManifest.xml`. |
| **Remove Premium Upsell** | `bytecodePatch` | `true` (Enabled) | None | Removes premium upsell surfaces. |
| **X MLKit Vision Slimmer** | `resourcePatch` | Opt-in | No | Disables MLKit discovery service + init provider and strips MLKit registrars. WARNING: breaks in-app QR/barcode scanning. |

---

## 3. Deep Technical Breakdown

### A. Layer 1: Manifest Purge Layer (`resourcePatch`)

The resource patch executes declarative AST transformations on `AndroidManifest.xml` before DEX assembly:

1. **Permission Stripping**:
   - `com.google.android.gms.permission.AD_ID` (Google Play Services Advertising ID)
   - `android.permission.ACCESS_ADSERVICES_AD_ID` (Android Privacy Sandbox Ad ID)
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` (Privacy Sandbox Attribution API)
   - `android.permission.ACCESS_ADSERVICES_TOPICS` (Privacy Sandbox Topics API)
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` (Play Install Referrer)

2. **Component Disabling**:
   Disables measurement and background transport components by setting `android:enabled="false"` and `android:exported="false"`:
   - `com.google.android.gms.measurement.AppMeasurementService`
   - `com.google.android.gms.measurement.AppMeasurementJobService`
   - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
   - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
   - `com.google.android.gms.measurement.AppMeasurementReceiver`
   - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`
   - `com.twitter.analytics.tracking.InstallationReferrer$OemIntentReceiver`

3. **Component Discovery Registrar Pruning**:
   Purges dependency injection registrars declared under `com.google.firebase.components.ComponentDiscoveryService` matching tracking keywords (`analytics`, `measurement`, `perf`, `remoteconfig`, `sessions`, `abt`), while strictly preserving ML Kit, Firebase Cloud Messaging, and Crashlytics registrars. Crashlytics registrars are preserved because the app requires the component at startup (collection suppressed via `firebase_crashlytics_collection_enabled=false`):
   - `FirebasePerfKtxRegistrar` / `FirebasePerfRegistrar`
   - `AnalyticsConnectorRegistrar`
   - `FirebaseRemoteConfigKtxRegistrar` / `RemoteConfigRegistrar`
   - `AbtRegistrar`
   - `FirebaseSessionsRegistrar`

4. **Opt-Out Metadata Injection**:
   Injects declarative configuration tags under `<application>` to disable SDK telemetry collection across initialization sequences:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

5. **Application Invariants & Non-Interference**:
   The manifest purge explicitly preserves:
   - Core network access permissions (`android.permission.INTERNET`, `android.permission.ACCESS_NETWORK_STATE`).
   - Push notifications via `com.twitter.notification.service.firebase.TwitterFirebaseMessagingService`, `com.google.firebase.messaging.FirebaseMessagingService`, and `com.google.firebase.provider.FirebaseInitProvider`.
   - Google Play Billing via `com.android.billingclient.api.ProxyBillingActivity` and `ProxyBillingActivityV2`.
   - Deep-link scheme filters (`twitter://`, `x://`, `https://x.com`, `https://twitter.com`).
   - User-facing internal WebView and analytics screens (`TweetAnalyticsWebViewActivity`, `AccountAnalyticsActivity`, `RoomHostAnalyticsWebViewActivity`).

### B. Layer 2: Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes SDK event submission pipelines at execution time:

1. **Targeted Entrypoint Hooks**:
   - `Lcom/google/android/gms/measurement/AppMeasurement;->logEventInternal(Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)V`

2. **Transformation Strategy**:
   - Injects immediate `return-void` at instruction index 0.
   - Prevents allocation of event payloads, dispatch thread creation, and SQLite metric buffering.
   - Restricts hooks to stable, unobfuscated third-party SDK signatures, eliminating fragility across target app updates.

3. **Telemetry & Zero-Zombie Standard**:
   - Emits structured diagnostic messages prefixed with `[X Telemetry]`.
   - Reports dynamic hook mutation counts without loop spam.
   - Follows zero-zombie verification to ensure all hooks cleanly resolve or prune on target updates.

### C. Layer 3: Premium Upsell Neutralization Layer (`bytecodePatch`)

The bytecode patch removes client-side premium upsell surfaces in navigation and composer/upload flows:

1. **Targeted Entrypoint Hooks**:
   - `subscriptions_upsells_premium_home_nav_enabled`: gates premium upsell surfaces in media and composer upload flows (`com.x.composer.upload.v2.ui`).
   - `subscriptions_upsells_premium_home_nav_offer_enabled`: gates premium upsell tab and promotional offer surfaces in home navigation tabbed layouts (`com.x.home.tabbed`).

2. **Transformation Strategy**:
   - Locates feature switch check call sites via indexed string literal references.
   - Neutralizes boolean evaluation results dynamically (forces return register to `0` / `false` or boxed `Boolean.FALSE`), short-circuiting upsell gating branches at client level.
   - Preserves standard navigation tabs and upload pipelines without server communication interference.

3. **Telemetry & Zero-Zombie Standard**:
   - Emits structured diagnostic messages prefixed with `[Remove Premium Upsell]`.
   - Reports dynamic hook mutation counts without loop spam (`Applied $patched hooks -> premium upsell surfaces suppressed.`).
   - Guarantees zero zombie mismatches via strict runtime validation on flag evaluation resolution.

Known layout limitation: on the APKM distribution all native code ships in APK splits, which the patcher passes through sign-only, so X Crash Native Slimmer logs a skip on this target and frees 0 bytes; it activates on standalone-APK layouts carrying bundled libs.
