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
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Removes promoted posts, trends and ads from timeline. |
| **Hide Banner** | `bytecodePatch` | `true` (Enabled) | None | Hides new post banner. |
| **Hide Promote Button** | `bytecodePatch` | `true` (Enabled) | None | Hides promote button under self posts. |
| **Hide Nudge Button** | `bytecodePatch` | `true` (Enabled) | None | Hides follow/subscribe/follow back buttons on posts. |
| **Hide Recommendation Items** | `bytecodePatch` | `true` (Enabled) | None | Hides recommendation items such as Who to follow and Today's news in timeline, search, and replies. |
| **Hide Recommended Users** | `bytecodePatch` | `true` (Enabled) | None | Hides recommended users popup shown when following someone. |
| **Hide Live Threads** | `bytecodePatch` | `true` (Enabled) | None | Hides live threads. |
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

### D. Layer 4: Timeline Ads Suppression Layer (`bytecodePatch`)

The bytecode patch removes promoted content from the timeline on the 12.33 native URT stack (`com.x.urt.items`, replacing the removed `JsonTimeline*` mapper layer):

1. **Targeted Entrypoint Hooks**:
   - `Lcom/x/urt/b;->a(...)`: presenter dispatch for timeline items; filters promoted posts (`k1` with `ef` promoted metadata), promoted trends (`z1` with `sf` metadata), and entry IDs containing `promoted`.
   - Google SSP init (`googlessp/init`): short-circuits ad SDK initialization to `Boolean.FALSE`.
   - `Lcom/x/urt/items/post/quickpromote/b;->a(...)`: suppresses the QuickPromote booster surface.

2. **Transformation Strategy**:
   - Injects an item-type gate at presenter dispatch returning the empty `b0` presenter for promoted items.
   - Forces ad-init result to false client-side; no network payloads touched.

3. **Telemetry & Zero-Zombie Standard**:
   - Emits structured diagnostic messages prefixed with `[Remove Ads]`.
   - Reports dynamic hook mutation counts without loop spam (`Applied $patched hooks -> promoted content and timeline ads suppressed.`).
   - Guarantees zero zombie mismatches via strict runtime validation on fingerprint resolution.

### E. Layer 5: Served-Content Debloat (`bytecodePatch`)

The bytecode layer suppresses intrusive server-driven prompts, banners, and served content overlays at the client presentation boundary:

1. **Strategy & Telemetry Standards**:
   - Anchors obfuscated instruction models dynamically via unique string literals without APK-wide method scanning.
   - Neutralizes presentation state models at instantiation time to short-circuit downstream Jetpack Compose rendering trees cleanly.
   - Structured telemetry prefixed with `[<Patch Name>]` reports exact mutation counts under a zero-zombie policy (`error()` on unresolved targets).

2. **Suppressed Surfaces**:
   - **Hide Banner**:
     - Target: `UrtShowInstructionsState` constructor (`com.x.urt.instructions`), resolved dynamically via indexed string `UrtShowInstructionsState(showInstructions=`.
     - Transformation: Neutralizes constructor instructions by replacing `showInstructions` with `Collections.emptyList()` and zeroing all eligibility/visibility flags (`isEligibleToShowPill`, `isReadyToShow`, `isPillCurrentlyVisible`).
   - **Hide Promote Button**:
     - Target: Post presentation quick promote eligibility evaluator (`com.x.urt.items.post`), resolved dynamically via indexed strings `x_lite_quick_promote_enabled` and `x_lite_quick_promote_premium_paywall_enabled`.
     - Transformation: Forces method return to `false` (`0`), suppressing self-post promote button rendering (`isQuickPromoteEligible`).
   - **Hide Nudge Button**:
     - Target: `FocalPostState` constructor (`com.x.urt.items.post`), resolved dynamically via indexed string `FocalPostState(timelinePostState=`.
     - Transformation: Neutralizes constructor instructions by zeroing `followButtonState` (`null`), `isSubscribeEligible` (`false`), and stateful follow flags before field assignment, suppressing post follow/subscribe/follow back button rendering.
   - **Hide Recommendation Items**:
     - Target: Timeline presenter dispatch (`Lcom/x/urt/b;->a`), resolving item models implementing `Lcom/x/models/timelines/items/p0;`.
     - Transformation: Intercepts timeline items whose `entryId` contains recommendation identifiers (`who-to-follow`, `who_to_follow`, `who-to-subscribe`, `stories`, `eventsummary`, `toptabsrpusermodule`, `community-to-join`) and returns the empty presenter (`Lcom/x/urt/b0;->a`) to suppress rendering.
   - **Hide Recommended Users**:
     - Target: `ProfileFollowRecommendationResponse` constructors (`com.twitter.profile.api`), resolved dynamically via indexed string `ProfileFollowRecommendationResponse(style=`.
     - Transformation: Neutralizes constructor instructions by zeroing `recommendedUsers` (`null`) before field assignment, preventing follow recommendation models from entering presenter state and suppressing recommended users popup rendering.
   - **Hide Live Threads**:
     - Target: `FleetlineApiResponse` constructor (`com.x.http.spaces`), resolved dynamically via indexed string `FleetlineApiResponse(threads=`.
     - Transformation: Neutralizes constructor instructions by replacing `threads` with `Collections.emptyList()` before field assignment, suppressing live thread and space bar rendering at the top of the timeline.

Known layout limitation: on the APKM distribution all native code ships in APK splits, which the patcher passes through sign-only, so X Crash Native Slimmer logs a skip on this target and frees 0 bytes; it activates on standalone-APK layouts carrying bundled libs.
