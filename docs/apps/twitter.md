# X (Twitter) Patches Guide

Technical documentation and patch catalog for X (Twitter) on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | X |
| **Package Name** | `com.twitter.android` |
| **Target Version** | `12.35.0-prod.01` |
| **Target Package Format** | APKM |
| **Primary Architecture** | `arm64-v8a` |
| **Authoritative Source** | APKMirror |
| **Download Target** | [X 12.35.0-prod.01 (arm64-v8a, 480-640dpi, versionCode 312350001)](https://www.apkmirror.com/apk/x-corp/x/x-12-35-0-prod-01-release/) |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Background Slimmer** | `resourcePatch` | `false` (Opt-in) | None | Disables background WorkManager tasks, widgets, and Cast/NFC companion services to reduce resource usage. |
| **Block Telemetry & Trackers** | `bytecodePatch` | `true` (Enabled) | None | Blocks telemetry collection, redirects Braze, Sentry, Google Ads/DoubleClick, and Firebase Crashlytics remote settings endpoints to 0.0.0.0, and purges tracking and Sentry configuration from manifest. Includes opt-in toggle to block X Payments telemetry. |
| **Block Update Screen** | `bytecodePatch` | `true` (Enabled) | None | Blocks outdated app warning screens and suppresses update prompts on launch. |
| **Control Video Auto Scroll** | `bytecodePatch` | `false` (Opt-in) | None | Prevents automatic scrolling to the next video in immersive media view. |
| **Custom Deep Links** | `resourcePatch` | `false` (Opt-in) | None | Registers intent filters to open supported domains in the app. |
| **Custom Fonts** | `bytecodePatch` | `false` (Opt-in) | None | Applies custom typography to post and profile text. |
| **Default Reply Sorting** | `bytecodePatch` | `false` (Opt-in) | None | Opens replies on every post with the selected sort mode (Likes by default) instead of Relevance. |
| **Disable Auto Refresh** | `bytecodePatch` | `false` (Opt-in) | None | Stops the home timeline from automatically jumping to new posts. |
| **DM Debloat** | `bytecodePatch` | `false` (Opt-in) | None | Hides call buttons and composer actions in direct message conversations. |
| **Drawer Items** | `bytecodePatch` | `false` (Opt-in) | None | Hides navigation drawer items including Grok, Premium, and secondary shortcuts. |
| **Navigation UI** | `bytecodePatch` | `false` (Opt-in) | None | Customizes navigation bar, unread badges, search tabs, and floating action button. |
| **No Shortened URL** | `bytecodePatch` | `false` (Opt-in) | None | Expands t.co shortened links to their direct destination. |
| **Pause Search Suggestions** | `bytecodePatch` | `false` (Opt-in) | None | Stops saving recent search history locally without breaking search. |
| **Post & Profile UI** | `bytecodePatch` | `false` (Opt-in) | None | Hides post action buttons and customizes profile surfaces. |
| **Remove Ads** | `bytecodePatch` | `true` (Enabled) | None | Removes promoted posts, timeline ads, and sponsored trends. |
| **Remove Premium Upsell** | `bytecodePatch` | `true` (Enabled) | None | Removes premium upgrade prompts and profile verification pills. |
| **Round Off Numbers** | `bytecodePatch` | `false` (Opt-in) | None | Displays exact counts instead of abbreviated numbers. |
| **Share Links** | `bytecodePatch` | `false` (Opt-in) | None | Replaces shared post links with a custom domain. |
| **Show Sensitive Media** | `bytecodePatch` | `false` (Opt-in) | None | Displays sensitive media directly without tap-to-reveal blur warnings. |
| **Timeline UI** | `bytecodePatch` | `true` (Enabled) | None | Customizes timeline surfaces including banners, recommendation carousels, and action buttons. |
| **Unlock Video Downloads** | `bytecodePatch` | `true` (Enabled) | None | Lets you save videos and GIFs with the built-in download option without Premium. |

---

## 3. Deep Technical Breakdown

### A. Manifest & Background Optimization Layer (`resourcePatch`)

1. **Background Slimmer**:
   - **Objective**: Reduce background battery and memory usage.
   - **How**: Disables background periodic jobs, home screen widgets, and Cast/NFC companion services.

2. **Custom Deep Links**:
   - **Objective**: Open custom domain links (inbound routing) directly in the application.
   - **How**: Injects intent filters with specified domains (vxtwitter, fixvx, fxtwitter, fixupx, twittpr, xcancel) into MainActivity in the manifest.

### B. Bytecode Modification Layer (`bytecodePatch`)

1. **Block Telemetry & Trackers**:
   - **Objective**: Stop runtime metric and event submission, redirect tracker endpoints, and strip tracking components.
   - **How**: Injects immediate returns into measurement and performance tracking methods; redirects Braze API base URLs, Sentry ingest endpoints, Google Ads/DoubleClick, and Firebase Crashlytics remote settings endpoints to `http://0.0.0.0`; provides an opt-in toggle (`blockPaymentsTelemetry`, default `false`) to redirect Plaid, ScribeUp, and anti-fraud financial endpoints; automatically executes internal manifest purge to strip advertising permissions, disable analytics services, and purge Sentry auto-initialization metadata.

2. **Block Update Screen**:
   - **Objective**: Prevent outdated-app blocking screens on launch.
   - **How**: Intercepts update checks and suppresses update requirement prompts.

3. **Control Video Auto Scroll**:
   - **Objective**: Stop automatic advancement to the next video in immersive mode.
   - **How**: Modifies video configuration state initialization to disable auto-advance behavior.

4. **Custom Fonts**:
   - **Objective**: Apply user-selected typography to application text.
   - **How**: Replaces font asset paths with configured font file paths.

5. **Default Reply Sorting**:
   - **Objective**: Open replies sorted by the selected mode (Likes, Recency, or Relevance) instead of always by Relevance; the app does not remember the sort chosen in the reply-sorting sheet.
   - **How**: Rewrites the three hard-coded `Relevance` reads of the ranking-mode enum (repository seed, Compose UI-state seed, conversation prefetch) to the selected mode; all three sites are resolved before any is rewritten.

6. **Disable Auto Refresh**:
   - **Objective**: Prevent the home timeline from automatically jumping to new posts.
   - **How**: Skips automatic timeline fetches in the event dispatcher, the background refresh worker, and the tab-selection callback (live updates, resume/startup reloads, and viewport-triggered refreshes), keeping the reading position; manual pull-to-refresh and pagination keep working.

7. **DM Debloat**:
   - **Objective**: Simplify direct message conversation headers.
   - **How**: Toggles conversation action flags to hide call buttons and composer actions.

8. **Drawer Items**:
   - **Objective**: Declutter the side navigation drawer and sidebar surfaces.
   - **How**: Hides configured navigation entries including Grok assistant, Premium, and secondary shortcuts.

9. **Navigation UI**:
   - **Objective**: Customize navigation bars, floating buttons, and tabs.
   - **How**: Adjusts layout flags to hide unread badges, floating action buttons, and secondary tabs (drawer and sidebar toggles are centralized under Drawer Items).

10. **No Shortened URL**:
    - **Objective**: Bypass t.co link redirection and click tracking.
    - **How**: Replaces shortened link destinations with their full original target URLs.

11. **Pause Search Suggestions**:
    - **Objective**: Stop storing recent search entries without breaking active search.
    - **How**: Suppresses local caching of search queries.

12. **Post & Profile UI**:
    - **Objective**: Remove unwanted action buttons from posts and profiles.
    - **How**: Hides Grok post actions and profile buttons, and toggles profile header tabs.

13. **Remove Ads**:
    - **Objective**: Remove promoted posts and sponsored content.
    - **How**: Filters out promoted tweets and advertising objects in timeline adapters.

14. **Remove Premium Upsell**:
    - **Objective**: Eliminate subscription prompts and badges.
    - **How**: Hides verification upsell pills and premium upgrade banners (drawer Premium entry is owned by Drawer Items).

15. **Round Off Numbers**:
    - **Objective**: Show exact metric counts on posts and profiles.
    - **How**: Disables number abbreviation formatting routines.

16. **Share Links**:
    - **Objective**: Clean and customize links when sharing posts (outbound).
    - **How**: Replaces shared link domains with configured custom domains (inbound link opening is handled by Custom Deep Links).

17. **Show Sensitive Media**:
    - **Objective**: View sensitive media without manual tap-to-reveal prompts.
    - **How**: Sets sensitive media warning flags to display media directly.

18. **Timeline UI**:
    - **Objective**: Clean up home timeline feeds.
    - **How**: Removes banner alerts, recommendation carousels, and video entrypoints.

19. **Unlock Video Downloads**:
    - **Objective**: Save videos and GIFs using the native download action without Premium.
    - **How**: Marks every video and GIF as downloadable so the native "Download video" option is shown, and lets the Premium check pass only at the three native download entry points (media viewer, post menu, video tab); the Premium check itself and its other callers stay untouched; downloads use the app's own downloader.

---

## 4. Behavioral Hazards Warning

1. **DM Debloat**:
   Neutralizing the calling flag disables direct message outgoing call entrypoints.
3. **Block Telemetry & Trackers (`blockPaymentsTelemetry`)**:
   Enabling the opt-in `blockPaymentsTelemetry` option redirects Plaid, ScribeUp, and anti-fraud telemetry endpoints to `http://0.0.0.0`, which disables X Payments and banking verification features.

---

## 5. Known Limitations

None evidenced by patch sources.

---

## 6. Canonical Universal Patches

The MLKit slimming removed from this guide is covered by the `Block Trackers` universal patch in the canonical collection [kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches): enable its `disableMlKit` option to disable `MlKitInitProvider`, the component discovery service and the vision registrars (same warning applies: in-app QR and barcode scanning breaks). That collection also provides further generic maintenance (`Remove Unused Screen Sizes`, `Remove Extra Languages`, `Clean App Packaging`, `Stop Background Sync`, `Remove Tracking Libraries`). Add that source in Morphe Manager alongside this one to use them; universal patches apply to any APK, including X.
