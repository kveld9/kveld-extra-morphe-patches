# PowerPoint Patches Guide

Technical documentation and patch catalog for Microsoft PowerPoint on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | PowerPoint |
| **Package Name** | `com.microsoft.office.powerpoint` |
| **Target Version** | `16.0.20527.20034` |
| **Target Package Format** | APKM (Split APK Bundle) |
| **Authoritative Source** | [APKMirror](https://www.apkmirror.com/apk/microsoft-corporation/powerpoint/microsoft-powerpoint-16-0-20527-20034-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Options | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `PowerPoint Telemetry Manifest Purge` | None | Neutralizes Microsoft OneDS/Aria lifecycle, aggregated-metric and failure-logging dispatch methods, nullifies ad measurement platform identifiers (AIFA, AppSetId), disables HockeyApp activities and DataTransport components, and strips advertising permissions. |
| **PowerPoint Telemetry Manifest Purge** | `resourcePatch` | `false` (Opt-in) | None | None | Strips tracking/advertising permissions, disables telemetry, HockeyApp activity and DataTransport components, and injects opt-out metadata in AndroidManifest.xml. |
| **Bypass Code Transparency** | `bytecodePatch` | `true` (Enabled) | None | None | Bypasses the code transparency checks. |
| **Disable Login Requirement** | `bytecodePatch` | `true` (Enabled) | None | None | Removes login requirement and FTUX paywall screens. |
| **Unlock 365 Family** | `bytecodePatch` | `true` (Enabled) | None | None | Unlocks Microsoft 365 Family subscription features locally. |
| **Remove Shared User ID** | `resourcePatch` | `true` (Enabled) | None | None | Removes the sharedUserId attribute from the manifest to prevent installation conflicts. |
| **PowerPoint Locale Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Locales to keep | Strips unselected localized resource directories (res/values-<locale>/) from PowerPoint base APK. English (en, en-us) is always retained. WARNING: Application strings for stripped locales will fall back to English. |
| **PowerPoint DPI Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Target screen density | Strips drawables for unselected screen densities from PowerPoint base APK while preserving launcher icons and single-density assets. WARNING: Displays matching stripped densities will scale preserved assets. |
| **PowerPoint Companion Native Slimmer** | `rawResourcePatch` | `false` (Opt-in) | None | None | Strips optional companion native binaries (React Native and Hermes JavaScript runtime stack) via in-situ zeroing. WARNING: stripped libraries are load-bearing for React Native initialization - enabling this WILL crash the app with UnsatisfiedLinkError when Copilot or other React Native surfaces start, not merely hide those features. |

---

## 3. Deep Technical Breakdown

### A. Layer 1: Manifest Purge Layer (`resourcePatch`)

The resource patch executes declarative AST transformations on `AndroidManifest.xml` before DEX assembly:

1. **Permission Stripping (6 Permissions)**:
   - `com.google.android.gms.permission.AD_ID` (Google Play Services Advertising ID)
   - `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` (Play Install Referrer)
   - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION` (Privacy Sandbox Attribution API)
   - `android.permission.ACCESS_ADSERVICES_AD_ID` (Android Privacy Sandbox Ad ID)
   - `android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE` (Privacy Sandbox Custom Audiences)
   - `android.permission.ACCESS_ADSERVICES_TOPICS` (Privacy Sandbox Topics API)

2. **Component Disabling**:
   Disables background transport, crash reporting, and cross-sell components by setting `android:enabled="false"` and `android:exported="false"`:
   - **Google DataTransport**:
     - `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService`
     - `com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver`
   - **HockeyApp Crash & Feedback Reporting**:
     - `com.microsoft.office.hockeyapp.activities.HockeyWebViewActivity`
     - `net.hockeyapp.android.FeedbackActivity`
     - `net.hockeyapp.android.UpdateActivity`
   - **Install & Referrer Tracking**:
     - `com.microsoft.office.asyncdatapointreporting.InstallBroadcastReceiver`
     - `com.microsoft.office.officehub.util.OHubBroadcastReceiver`
   - **Microsoft Cross-Sell & Campaign Targeting**:
     - `com.microsoft.android.crosssell.CrossSellAgentMarker`
     - `com.microsoft.android.crosssell.CrossSellReceiver`
     - `com.microsoft.android.crosssell.PackageStateReceiver`
     - `com.microsoft.android.crosssell.SelfReplacementReceiver`
     - `com.microsoft.android.crosssell.activities.ExcelCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.PdfCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.PowerpointCrossSellHandlerActivity`
     - `com.microsoft.android.crosssell.activities.WordCrossSellHandlerActivity`
     - `com.microsoft.office.floodgate.launcher.FloodgateDynamicUxActivity`

3. **Component Discovery Registrar Pruning**:
   Purges component discovery registrars declared under `<application>` matching:
   - `TransportRegistrar`
   - `datatransport`
   - `analytics`
   - `measurement`
   - `crashlytics`
   *(MLKit registrars matching `mlkit` are strictly preserved).*

4. **Opt-Out Metadata Injection (7 Metadata Flags)**:
   Injects declarative configuration tags under `<application>`:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

### B. Layer 2: Bytecode Telemetry Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes Microsoft OneDS/Aria telemetry and ad measurement pipelines:

1. **LifecycleHandler Hooks (7 Methods)**:
   Injects `return-void` at instruction index 0 in `Lcom/microsoft/applications/telemetry/core/LifecycleHandler;`:
   - `onActivityCreated(Activity, Bundle)`
   - `onActivityStarted(Activity)`
   - `onActivityResumed(Activity)`
   - `onActivityPaused(Activity)`
   - `onActivityStopped(Activity)`
   - `onActivitySaveInstanceState(Activity, Bundle)`
   - `onActivityDestroyed(Activity)`

2. **Aggregated Metric & Failure Dispatcher Hooks**:
   - `Lcom/microsoft/applications/telemetry/AggregatedMetric$SendAggregationTimerTask;->run()V`: Injects `return-void`.
   - `Lcom/microsoft/applications/telemetry/core/e0;->logFailure(String, String, String, String, EventProperties)V`: Injects `return-void`.
   - `Lcom/microsoft/applications/telemetry/core/e0;->logFailure(String, String, EventProperties)V`: Injects `return-void`.

3. **Power & Hardware Information Receivers (2 Methods)**:
   - `Lcom/microsoft/applications/telemetry/pal/hardware/HardwareInformationReceiver;->onMAMReceive(Context, Intent)V`: Injects `return-void`.
   - `Lcom/microsoft/unified/telemetry/mutsdk/PowerInfoReceiver;->onMAMReceive(Context, Intent)V`: Injects `return-void`.

4. **Ad Measurement Platform Identifiers (Empty String Nullification)**:
   Hooks identifier accessor methods in `Lcom/microsoft/office/adsmobile_admeasurementpartner/admeasurement/AdMeasurementPlatformData;`:
   - `getAIFA()Ljava/lang/String;`: Clears try blocks and returns empty string (`const-string v0, ""`, `return-object v0`).
   - `getAppSetId()Ljava/lang/String;`: Clears try blocks and returns empty string (`const-string v0, ""`, `return-object v0`).

### C. Login Requirement & FTUX Paywall Removal (`bytecodePatch`)

Removes mandatory sign-in and first-time user experience (FTUX) paywall screens via 5 targeted hooks:

1. **FTUX Completion Listener Hook**:
   Intercepts task completion in `firstRunM0Fingerprint` (checking `OHubSharedPreferences.isFTUXShown`), clears try blocks, and completes with success status (`TaskResult(0)` via `IOnTaskCompleteListener.onTaskComplete`).

2. **First Run Experience Mark Complete**:
   Intercepts `firstRunN0Fingerprint` (matching string `"FRE Completed"`), resolves the current activity via `OfficeActivityHolder.GetActivity()`, and invokes `OHubSharedPreferences.setFTUXShown(activity, true)`.

3. **FTUX Paywall Launcher Interception**:
   Intercepts `ftuxPaywallLauncherFingerprint` (`DrillInDialog` launcher), clears try blocks, and invokes `onTaskComplete(new TaskResult(0))`.

4. **Sign-In Identity Nullification**:
   Intercepts `IdentityLiblet.GetIdentityForSignInName` (`getIdentityForSignInNameFingerprint`), clears try blocks, and returns `null` (`const/4 v0, 0x0`, `return-object v0`).

5. **SSO File Activation Bypass**:
   Intercepts `FileActivationSSOManager.checkAndStartSSOIfRequired` (`checkAndStartSSOIfRequiredFingerprint`), clears try blocks, and returns `false` (`const/4 v0, 0x0`, `return v0`).

### D. Microsoft 365 Family Unlocking (`bytecodePatch`)

Locally unlocks Microsoft 365 Family subscription features and bypasses upsell gates:

1. **Licensing State Overrides**:
   - `OHubUtil.GetLicensingState()` (`getLicensingStateFingerprint`): Returns `LicensingState.ConsumerPremium`.
   - License session state (`licenseSessionStateFingerprint` calling `LicensingState.FromInt`): Returns `LicensingState.ConsumerPremium`.

2. **Subscription Plan Flags**:
   - `LicenseInfo.HasFamilyPlan()` (`hasFamilyPlanFingerprint`): Returns `true` (`const/4 v0, 0x1`).
   - `LicenseInfo.HasPersonalPlan()` (`hasPersonalPlanFingerprint`): Returns `true` (`const/4 v0, 0x1`).
   - `LicenseInfo.HasPremiumPlan()` (`hasPremiumPlanFingerprint`): Returns `true` (`const/4 v0, 0x1`).

3. **Entitlements Generation**:
   - Glifu native proxy licensing hook (`licensingFGFingerprint`): Constructs an empty `OlsEntitlement[]` array and returns a initialized `LicenseInfo` instance.

4. **Upsell, Trial & Quota Suppression**:
   - `SaveFlowUpsell` entry point (`subscriptionStatusYFingerprint`): Replaced with `return-void`.
   - `PlatFeatureGateHelper.isPremiumPlanUpsellEnabled()` (`isPremiumPlanUpsellEnabledFingerprint`): Returns `false` (`const/4 v0, 0x0`).
   - `PlatFeatureGateHelper.IsEnterpriseViewOLSCheckEnabled()` (`isEnterpriseViewOLSCheckEnabledFingerprint`): Returns `false` (`const/4 v0, 0x0`).
   - `SubscriptionData.isTrial()` (`subscriptionDataIsTrialFingerprint`): Returns `false` (`const/4 v0, 0x0`).
   - `isPremium()` in upsell plugin models (`licenseStatusIsPremiumFingerprint`): Returns `true` (`const/4 v0, 0x1`).
   - `AccountProfileInfo.hasProfile()` (`accountProfileInfoHasProfileFingerprint`): Returns `true` (`const/4 v0, 0x1`).
   - Storage quota check (`storageQuotaCheckFingerprint`): Returns `false` (`const/4 v0, 0x0`).

5. **Account Switcher Guard**:
   - `accountSwitcherRunnableFingerprint` in `AccountActionsController.setAccountInfoDialog`: Injects `if-nez v12, :has_identity` guard to return early (`return-void`) when identity is null, avoiding null-pointer exceptions in the account switcher UI.

### E. Code Transparency Bypass (`bytecodePatch`)

Bypasses code transparency integrity assertions:
- Hooks `codeTransparencyCheckFingerprint` (`CodeTransparencyCheckCallback`).
- Clears try blocks, invokes `callback.transparencyVerificationSucceeded()`, and returns void.

### F. Shared User ID Removal (`resourcePatch`)

Removes `android:sharedUserId` and `android:sharedUserLabel` from the root `<manifest>` tag in `AndroidManifest.xml`:
- Eliminates UID collision conflicts when installing alongside other Office packages with differing signatures.

### G. Resource Slimming Layers (`resourcePatch`)

1. **PowerPoint Locale Slimmer (`resourcePatch`)**:
   - Scans resource directories for `values-<locale>` subdirectories.
   - Deletes localized folders whose language tag is not in the configured list (`targetLocales`, default `"en"`).
   - Preserves base fallback strings (`res/values`) and English variants (`en`, `en-us`).

2. **PowerPoint DPI Slimmer (`resourcePatch`)**:
   - Scans drawable and mipmap directories for density qualifiers (`ldpi`, `mdpi`, `tvdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).
   - Prunes directories not matching the configured target density (`targetDpi`, default `"xxhdpi"`).
   - Preserves launcher icons (`ic_powerpoint`, `ic_launcher`, `ic_launcher_round`, and manifest icons), `nodpi`, `anydpi`, non-DPI drawable directories, and single-density orphan assets.
   - Purges non-phone UI mode qualifiers (`watch`, `television`, `car`, `vrheadset`).

### H. Companion Native Binary Slimming Layer (`rawResourcePatch`)

Performs in-situ zeroing of unneeded companion native libraries in `lib/arm64-v8a` and `lib/armeabi-v7a`:
- Targeted libraries:
  - `libhermes.so`
  - `libreactnative.so`
- Validates the standard 4-byte ELF magic header (`0x7F`, `'E'`, `'L'`, `'F'`) before zeroing file contents to 0 bytes via `raf.setLength(0L)`.
- Reclaims package storage without breaking APK directory offsets or compression headers.

---

## 4. Behavioral Hazards Warning

Users and packagers must observe the following risks before applying patches:

1. **Opt-in Status**:
   `PowerPoint Locale Slimmer`, `PowerPoint DPI Slimmer`, and `PowerPoint Companion Native Slimmer` are opt-in and disabled by default (`default = false`). `PowerPoint Telemetry Manifest Purge` is also opt-in (`default = false`), though chained as an automatic dependency under `Block Telemetry & Trackers`.

2. **DPI & Locale Fallback Consequences**:
   - Stripping densities removes native-resolution drawables. On devices with displays matching stripped densities, Android scales preserved density assets, which may cause minor asset blur or scaling artifacts.
   - Stripping locale directories removes translations. If the system language is stripped, application text falls back to English.

3. **Companion Native Slimmer Crash Hazard (`UnsatisfiedLinkError`)**:
   `libhermes.so` and `libreactnative.so` are load-bearing for the React Native runtime inside PowerPoint. Enabling `PowerPoint Companion Native Slimmer` WILL crash the application with `UnsatisfiedLinkError` when Copilot or other React Native-driven surfaces are initialized. Do not enable this patch if React Native or Copilot features are needed.

4. **Mandatory Backup & Verification**:
   Users must retain a clean backup copy of the original PowerPoint APKM bundle and verify the patched installation before replacing daily installations.
