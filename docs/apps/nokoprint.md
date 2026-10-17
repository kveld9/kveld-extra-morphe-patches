# NokoPrint Patches Guide

Technical documentation and patch catalog for NokoPrint on Android.

---

## 1. Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target App** | NokoPrint - WiFi, Bluetooth, USB |
| **Package Name** | `com.nokoprint` |
| **Target Version** | `5.28.6` |
| **Target Package Format** | XAPK bundle |
| **Primary Architecture** | universal (`arm64-v8a` / `armeabi-v7a` / `x86` / `x86_64`) |
| **Download Target** | [APKPure](https://d.apkpure.com/b/XAPK/com.nokoprint?versionCode=52806) |
| **Authoritative Source** | APKPure |

---

## 2. Applied Patches Catalog

| Patch Name | Typology | Default State | Dependencies | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Block Ads (Setup)** | `resourcePatch` | `false` (Opt-in) | None | Removes ad screens and ad startup components. |
| **Block Ads** | `bytecodePatch` | `true` (Enabled) | `Block Ads (Setup)` | Blocks ad loaders and unlocks ad-free status. |
| **Remove Extra Files** | `rawResourcePatch` | `true` (Enabled) | None | Deletes hidden ad files and trackers to shrink the app. |
| **Stop Background Sync (Setup)** | `resourcePatch` | `false` (Opt-in) | None | Turns off background alarms and schedulers. |
| **Stop Background Sync** | `bytecodePatch` | `true` (Enabled) | `Stop Background Sync (Setup)` | Stops background tasks that drain the battery. |
| **Block Trackers (Setup)** | `resourcePatch` | `false` (Opt-in) | None | Removes ad permissions and disables tracking components. |
| **Block Trackers** | `bytecodePatch` | `true` (Enabled) | `Block Trackers (Setup)` | Blocks analytics and ad tracking. |
| **Remove Extra Stores** | `resourcePatch` | `true` (Enabled) | None | Disables payment components for other app stores you do not use. |
| **Printer-Compatible Connections** | `resourcePatch` | `true` (Enabled) | None | Keeps HTTP cleartext for LAN printers and driver downloads and trusts user certificates. |
| **Skip Welcome Screen** | `bytecodePatch` | `true` (Enabled) | None | Skips the first-launch popup (the About menu still works). |

---

## 3. Technical Breakdown

### A. Ad Removal & Premium Unlock
- **Block Ads**:
  - *Objective*: Remove all banner, interstitial, and rewarded advertisements and bypass Google Play anti-tamper exit checks.
  - *How*: Disables ad components and startup initializers in the manifest, forces ad-free status flags to true, short-circuits banner and interstitial dispatchers, and stubs license validation.
- **Remove Extra Files**:
  - *Objective*: Reduce package size and remove leftover tracker assets.
  - *How*: Purges ad SDK HTML assets, web tracking templates, and mediation configuration files from application resources.

### B. Telemetry & Background Sync
- **Block Trackers**:
  - *Objective*: Prevent analytics tracking and advertising identifier data collection.
  - *How*: Strips advertising identifier permissions and stubs Firebase, Google Analytics, and ad attribution tracking methods.
- **Stop Background Sync**:
  - *Objective*: Stop background tasks and wake locks from consuming system resources and battery.
  - *How*: Disables periodic worker jobs, alarm schedulers, and update check services in the manifest and runtime schedulers.

### C. Network Hardening & UX Cleanups
- **Remove Extra Stores**:
  - *Objective*: Remove unneeded payment libraries for alternative app stores.
  - *How*: Strips billing frameworks for Huawei AppGallery, Samsung Galaxy Store, and Amazon Appstore from the manifest.
- **Printer-Compatible Connections**:
  - *Objective*: Keep LAN printer discovery and driver downloads working while trusting user-installed certificates.
  - *How*: Updates the network security configuration to permit cleartext traffic (required by some printers) and adds user certificate trust. Less strict than stock; disable the patch for strict TLS-only.
- **Skip Welcome Screen**:
  - *Objective*: Open directly to the main printer workspace without displaying first-launch welcome dialogs.
  - *How*: Suppresses the initial welcome popup dialog while keeping the About screen accessible through settings.

---

## 4. Known Limitations

- **XAPK Container Format**: Target package is distributed as an XAPK container containing split architecture binaries.
- **Driver Download Connectivity**: Hardening network security preserves HTTP endpoints required by legacy network printers and remote driver repositories.
