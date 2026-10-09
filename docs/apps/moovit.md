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
| **Authoritative Source** | [APKMirror](https://www.apkmirror.com/apk/moovit/moovit-bus-train-live-info/moovit-your-transit-tracker-5-201-1-1809-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | `Moovit Telemetry Manifest Purge` | Neutralizes AppsFlyer, Braze, and Inneractive DEX dispatchers, disables analytics services/providers, and strips advertising permissions and AppKey. |
| **Fix Google Maps** | `bytecodePatch` | `true` (Enabled) | `Moovit Custom Maps API Key` | Restores Google Maps rendering by spoofing the original package signature to Google Play Services, with optional custom API key override. |
| **Unlock Moovit+** | `bytecodePatch` | `true` (Enabled) | None | Unlocks Moovit+ premium subscription features locally, including Safe Ride and address search in favorites. |
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Hides banner and inline ads and neutralizes ad unit ID lookups. |
| **Suppress Paywalls** | `bytecodePatch` | `true` (Enabled) | None | Suppresses subscription paywalls, onboarding upgrade dialogs, and promotional cards. |
| **Moovit Custom Maps API Key** | `resourcePatch` | `false` (Opt-in) | None | Replaces the Google Maps API key in AndroidManifest.xml and web-service strings when a custom key is provided. |
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

4. **Hardcoded Attribution AppKey Removal**:
   Purges `com.appsflyer.AppKey` from application `<meta-data>`, guaranteeing attribution kill independent of DEX drift.

5. **Component Discovery Registrars**:
   Dependency injection registrars under `com.google.firebase.components.ComponentDiscoveryService` are intentionally preserved (0 removed). The dependency injection graph in Moovit is coupled; suppression is achieved safely via opt-out metadata flags and disabled service/provider components.

6. **Opt-Out Metadata Injection**:
   Injects declarative configuration tags under `<application>` to disable SDK telemetry collection across initialization sequences:
   - `firebase_analytics_collection_enabled` = `false`
   - `firebase_analytics_collection_deactivated` = `true`
   - `firebase_crashlytics_collection_enabled` = `false`
   - `firebase_performance_collection_enabled` = `false`
   - `firebase_performance_collection_deactivated` = `true`
   - `firebase_sessions_enabled` = `false`
   - `google_analytics_adid_collection_enabled` = `false`
   - `google_analytics_default_allow_ad_personalization_signals` = `false`

7. **Manifest Purge Totals**:
   - 5 blocked permissions stripped.
   - 18 tracking components disabled.
   - 1 startup initializer removed.
   - 1 hardcoded attribution app key removed.
   - 8 opt-out metadata flags injected.

### B. Layer 2: Bytecode Dispatcher Neutralization Layer (`bytecodePatch`)

The Dalvik bytecode layer neutralizes SDK event submission pipelines at execution time via explicit `Fingerprint(...).method.apply` hooks:

1. **Targeted Entrypoint Hooks (10 Methods)**:
   - **AppsFlyer (`Lcom/appsflyer/internal/AFa1tSDK;`)**:
     - `start(Context)`: Rewritten to `return-void`.
     - `start(Context, String)`: Rewritten to `return-void`.
     - `start(Context, String, AppsFlyerRequestListener)`: Rewritten to `return-void`.
     - `logEvent(Context, String, Map)`: Rewritten to `return-void`.
     - `logEvent(Context, String, Map, AppsFlyerRequestListener)`: Rewritten to `return-void`.
     *(Note: The public `AppsFlyerLib` facade is abstract in v6.18, so hooks target the verified concrete subclass; the explicit-Fingerprint gate fails loudly on drift).*
   - **Braze (`Lcom/braze/Braze;`)**:
     - `requestImmediateDataFlush()`: Rewritten to `return-void`.
     - `openSession(Activity)`: Rewritten to `return-void`.
     - `logCustomEvent(String, BrazeProperties)`: Rewritten to `return-void`.
   - **Fyber Inneractive (`Lcom/fyber/inneractive/sdk/external/InneractiveAdManager;`)**:
     - `initialize(Context, String)`: Rewritten to `return-void`.
     - `wasInitialized()`: Rewritten to return `const/4 v0, 0x0` (`false`).

2. **Deliberately NOT Hooked**:
   - **Firebase Installations FID**: Push-breakage risk; accepted FCM dependency.
   - **Kinesis uploader**: May carry live transit data required for transit navigation.
   - **First-party `anonymousstream.moovitapp.com` / Zendesk**: Functional application requirements.
   - **`openSession` overload variation**: Activity variant hooked; non-Activity variants not present in runtime path.

3. **Device Traffic Re-Audit Outcome (Redmi Note 5, arm64)**:
   Controlled experiment verifying cold start network egress:
   - **Pre-fix Baseline**: Cold start generated network egress to `sdk.fra-01.braze.eu`, both AppsFlyer endpoints, and `cdn2.inner-active.mobi`.
   - **Post-fix Verification**: Capture (993 packets, non-empty, live transit app traffic positively present) showed NONE of those endpoints.
   - **Residuals Disposition**:
     - Single Braze config handshake (~944B out): Dispatch, session, and flush entrypoints are all stubbed; `Braze.configure` does not exist in this SDK version so init kill is impossible via stable API; stubbing `getInstance` causes NullPointerException in app callers.
     - Kinesis stream: Functional transit stream.
     - Firebase Installations + Crashlytics-settings fetch: Push and crash dependencies.
     - Zendesk: In-app support.
     - Lab environment notice: `api.twitter.com` and `edge.prelude.dev` observed during network captures are unattributed lab-device background chatter (no Twitter or Prelude SDKs exist in Moovit DEX) and are explicitly NOT claimed as app traffic.

### C. Google Maps Signature Spoofing (`bytecodePatch` + Companion Extension)

When Moovit is re-packaged or signed with custom keys, Google Play Services rejects Google Maps SDK authentication due to certificate mismatch with the registered API key:
- **Hook Point**: Injects an initialization call to `MoovitHelper.init()` in `MoovitApplication.onCreate`.
- **Companion Extension Payload**: Bundles `MoovitHelper.java` via `extensions/extension.mpe`.
- **Runtime Hook**:
  - Uses `HiddenApiBypass` to bypass hidden API restrictions on Android P+.
  - Dynamically proxies `ActivityThread.sPackageManager` and `ServiceManager.sCache["package"]`.
  - Intercepts `getPackageInfo` and `getPackageInfoAsUser` calls for `com.tranzmate` to return the official Tranzmate signing certificate to Google Play Services.
  - Guarantees full map tile loading, geocoding, and routing overlays without modifying host API keys.
- **Optional Custom Maps API Key (`mapsApiKey`)**: Users running in MicroG or Google-free environments can supply their own Google Maps Platform API key. When supplied, the chained `resourcePatch` replaces `com.google.android.geo.API_KEY` in `AndroidManifest.xml` and `google_wla_api_key` in `res/values/strings.xml`. When omitted (default), signature spoofing handles the built-in keys automatically without requiring user credentials.

### D. Moovit+ Premium Unlocking (`bytecodePatch`)

Unlocks subscription entitlement gates and premium features locally:
- **Subscription State Gate**: Rewrites `b()Z` in the `subscribed_skus` wrapper (`Ll1g;`) to return `true` unconditionally.
- **Subscription Package State**: Forces `com.moovit.app.subscription.premium.packages.a.b()` to return `SubscriptionPackageState.ACTIVE`.
- **Safe Ride Feature Gate**: Forces `com.moovit.app.subscription.premium.packages.safety.b.a()` to return `SubscriptionPackageState.ACTIVE`.
- **Favorite Location Address Search**: Flips constructor parameter `c` (`addAddressProvider`) from `false` to `true` in `FavoriteLocationEditorActivity.h1()`, enabling geocoded exact address searches rather than restricting favorite searches solely to transit stop identifiers.

### E. Ad Suppression & Removal (`bytecodePatch`)

Eliminates banner and inline advertisements across all views:
- **Ad Unit Resolver Suppression**: Hooks `getAdUnitId(AdSource)` in `Lg3b;` (containing remote-config marker `"is_interstitial_ads_free_version"`) to return empty strings (`""`), neutralizing both primary and fallback remote ad inventory requests.
- **MoovitAdView & MoovitBannerAdView View Suppression**: Hooks `setAdSource` on both banner classes (`com.moovit.app.ads.MoovitAdView` and `com.moovit.app.ads.MoovitBannerAdView`) to invoke `setVisibility(View.GONE)` before any view inflation or ad request initiation.
- **Ad-Free Menu Item Suppression**: Injects `setVisibility(View.GONE)` in `AdFreeMenuItemFragment.onCreateView()` right before returning the inflated view hierarchy.

### F. Paywall & Upgrade Dialog Suppression (`bytecodePatch`)

Suppresses modal paywalls, onboarding upgrade interstitials, and promotional upsell cards:
- **Remote Paywall Gate**: Rewrites `Lmj1.a(MoovitComponentActivity)Z` (remote config `"block_paywall"`) to return `false` unconditionally.
- **BlockPaywallActivity & Onboarding Interstitials**: Intercepts `onReady` in `BlockPaywallActivity` to invoke the dynamically resolved skip helper (the private method calling `getActivityToStartOnFinish()`), and in `MoovitPlusOnboardingActivity` to invoke the dynamically resolved skip helper (the method referencing `"activity_to_start_on_finish"`), ensuring resilience across future R8 obfuscation changes.
- **Promo Dialogs & Menu Items**: Suppresses `MoovitPlusActivity`, menu promo fragments, `MoovitSubscriptionsPromoCellFragment`, and dismisses `MoovitPlusPackagePopupFragment`.
- **Go Premium Card Suppression**: Neutralizes the "Go Premium" card visibility emitter in `Lynb;->emit()` by replacing the `VISIBLE` branch move operand (`move v8, v4`) with the `GONE` operand (`move v8, v2`).

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
