# Instagram Patches Guide

Technical documentation and patch catalog for Instagram on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | Instagram |
| **Package Name** | `com.instagram.android` |
| **Target Version** | `447.0.0.55.81` |
| **Target Package Format** | APKM (Split APK Bundle) |
| **Authoritative Source** | [APKMirror](https://www.apkmirror.com/apk/instagram/instagram-instagram/instagram-447-0-0-55-81-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Instagram Telemetry Manifest Purge` | Neutralizes Facebook Analytics2, DataTransport, FDID/PhoneId providers, and strips AD_ID permissions. |
| **Instagram Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | Strips tracking/advertising permissions and disables analytics services, providers, and receivers in AndroidManifest.xml. |
| **MLKit Vision Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disable MLKit component discovery and registrars. WARNING: this breaks in-app QR and barcode scanning. |

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
     - `com.instagram.common.analytics.fdidlite.AsyncInstagramFDIDLiteProvider`
     - `com.instagram.common.analytics.phoneid.AsyncInstagramPhoneIdProvider`
     - `com.instagram.contentprovider.InstallReferrerProvider`
   - **Services**:
     - `com.facebook.analytics2.fabric.onefabric.FFAlarmUploadJobService`
     - `com.facebook.analytics2.logger.GooglePlayUploadService`
     - `com.facebook.analytics2.logger.legacy.uploader.AlarmBasedUploadService`
     - `com.facebook.analytics2.logger.legacy.uploader.Analytics2UploadService`
     - `com.facebook.analytics2.logger.legacy.uploader.LollipopUploadService`
     - `com.facebook.analytics2.logger.service.LollipopUploadSafeService`
     - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
     - `com.google.android.gms.analytics.AnalyticsJobService`
     - `com.google.android.gms.analytics.AnalyticsService`
     - `com.meta.mfa.service.MfaCrossAppServiceImpl`
     - `com.meta.trusteddevice.service.TrustedDeviceFoundationServiceImpl`
     - `com.meta.wearable.acdc.sdk.service.ACDCRegistrationService`
   - **Receivers**:
     - `com.facebook.analytics2.fabric.onefabric.OneFabricUploadAlarmReceiver`
     - `com.facebook.analytics2.logger.legacy.uploader.HighPriUploadRetryReceiver`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`
     - `com.google.android.gms.analytics.AnalyticsReceiver`
     - `com.instagram.analytics.uploadscheduler.AnalyticsUploadAlarmReceiver`
     - `com.instagram.common.analytics.phoneid.InstagramPhoneIdRequestReceiver`
   - **Activities**:
     - `com.google.android.play.core.common.PlayCoreDialogWrapperActivity`

3. **Component Discovery Registrar Pruning**:
   Purges component discovery registrars declared under `<application>` matching:
   - `dynamicloading.DynamicLoadingRegistrar`
   - `mlkit`

### B. Layer 2: Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes Facebook Analytics2 event dispatchers at execution time:

1. **Targeted Entrypoint Hooks**:
   - `Lcom/instagram/analytics/analytics2/IgAnalytics2TaskBasedUploader;->HZG(LX/KpT;LX/ArQ;LX/Av0;)V`
   - `Lcom/instagram/analytics/analytics2/IGAnalytics2SimpleUploader;->HZG(LX/KpT;LX/ArQ;LX/Av0;)V`
   - `Lcom/facebook/analytics2/logger/legacy/uploader/PrivacyControlledUploader;->HZG(LX/KpT;LX/ArQ;LX/Av0;)V`

2. **Transformation Strategy**:
   - Injects immediate `return-void` at instruction index 0.
   - Prevents background payload formatting, compression, and network transmission across both simple and task-based Facebook Analytics2 upload pipelines.

3. **Telemetry Standard**:
   - Emits structured diagnostic messages prefixed with `[Instagram Telemetry]`.
   - Reports exact count and names of neutralized dispatch methods.

### C. Layer 3: MLKit Vision Slimmer Layer (`resourcePatch`)

Strips Google MLKit discovery services and component metadata from `AndroidManifest.xml`:

1. **Targeted Discovery Service**:
   - `com.google.mlkit.common.internal.MlKitComponentDiscoveryService` disabled via `android:enabled="false"` and `android:exported="false"`.

2. **Registrar Removal**:
   - Traverses `<meta-data>` children inside the discovery service and removes all elements with `android:name` prefixed with `com.google.firebase.components:`.

3. **Telemetry Standard**:
   - Emits structured diagnostic messages prefixed with `[Instagram MLKit Slimmer]`.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following risks before applying patches:

1. **Opt-in Status**:
   `MLKit Vision Slimmer` (`instagramMlKitSlimmerPatch`) is opt-in and disabled by default (`default = false`). `Instagram Telemetry Manifest Purge` (`instagramTelemetryResourcePatch`) is also opt-in (`default = false`), though chained as an automatic dependency under `Block Telemetry & Trackers`.

2. **MLKit QR & Barcode Scanning Breakage**:
   Applying `MLKit Vision Slimmer` disables Google MLKit vision component discovery. While reducing initialization overhead and background component loading, this breaks in-app QR code and barcode scanning functionality within Instagram.

3. **Mandatory Backup & Verification**:
   Users must retain a clean backup copy of the original Instagram APKM bundle and verify the patched installation before replacing daily installations.
