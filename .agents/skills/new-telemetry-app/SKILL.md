---
name: new-telemetry-app
description: End-to-end workflow for onboarding a new target app and authoring telemetry-blocking patches: reconnaissance, registration, patch authoring, verification gates, device smoke test, and network traffic auditing.
---

# New Target App Onboarding & Telemetry Hardening

## 1. Scope & Non-Goals

### A. In-Scope Objectives
- Surgical neutralization of tracking, analytics, crash-reporting, attribution, and advertising SDKs for strictly ONE new target application version.
- Multi-tier defense-in-depth suppression:
  1. Manifest-level entrypoints: advertising permissions, analytics background services, install referrer providers, broadcast receivers, and startup initializers.
  2. Declarative SDK opt-out flags: injecting disable flags directly into application `<meta-data>`.
  3. Dalvik bytecode dispatchers: stubbing telemetry loggers, dispatchers, and SDK initialization methods with zero runtime overhead.
  4. Native binary endpoints (where applicable): in-situ redirection of hardcoded native telemetry hosts in `.so` libraries to `0.0.0.0`.

### B. Explicit Non-Goals & Architectural Boundaries
- **Preserve Core Networking**: Never remove `android.permission.INTERNET` or sever primary application network channels.
- **Preserve Essential App Features**: Never break push notifications (FCM/GCM registration and receivers), deep links, account authentication, session renewal, media playback, or primary user interactions unless explicitly requested by the user.
- **Strict Prohibition of In-App Settings Screens**:
  - Never inject preference screens, settings activities, floating overlays, or dynamic runtime toggles into target applications.
  - *Rationale*: Dynamic UI panels introduce extreme fragility across weekly upstream obfuscation shifts and add disk I/O on performance-critical paths (authoritative boundary: `docs/out-of-scope.md`).
  - *Controlled Exception (Gboard Lite)*: Gboard Lite is the sole exception where in-app settings are permitted because it exposes standard AndroidX `PreferenceScreen` XML resources and standard IME settings activities.
  - *Standard for All Other Targets*: All configurable parameters must be compile-time / patch-time options via Morphe Manager / CLI (`stringOption`, `booleanOption`).
- **Strict Prohibition of Server-Side Bypasses & DRM**:
  - Never attempt to bypass server-side subscription paywalls, unlock cloud-restricted content, access private accounts, or defeat DRM protections (authoritative boundary: `docs/out-of-scope.md`). Patches operate strictly on client-side bytecode and local assets.
- **Strict Prohibition of Feature Bloat & Download Managers**:
  - Never embed third-party media download engines, torrent clients, or custom UI skins inside host applications.
- **Single Target Version Invariant**:
  - Every target application must target strictly ONE active version (`targets = listOf(AppTarget(...))`).
  - Never retain legacy fallback code or multi-version compatibility matrices. When upstream updates, bump the target version and retire the old version immediately.

---

## 2. Architecture & Compatibility Policy

### A. CPU Architecture Support Matrix
- **`arm64-v8a` (Primary / First-Class)**: Standard 64-bit target for all modern Android devices. All patches, native transforms, and validation runs must target `arm64-v8a` first.
- **`armeabi-v7a` (Legacy 32-bit)**: Supported only when the application provides official 32-bit builds and patches operate strictly on Dalvik bytecode/resources without 64-bit-exclusive native dependencies.
- **`x86` / `x86_64` (Out of Scope)**: Desktop emulator architectures are not supported.

### B. APK Variant Selection
- **Standalone nodpi APK**: Preferred target format whenever available upstream.
- **Split APK Bundles (`.apkm` / `.xapk`)**: Used when upstream distributes only split bundles. Morphe Patcher and the test runner fuse split DEXes and assets into a unified base APK during patching.
- Target compatibility must declare the exact format: `apkFileType = ApkFileType.APK`, `ApkFileType.APKM`, or `ApkFileType.XAPK`.

### C. Universal vs Dedicated Patch Boundary
- **Precision Superset Rule**: Dedicated application patches are a precision superset for their respective targets.
- **Prohibition on Stacking Universals**: Never stack `Universal Telemetry Neutralizer` or `Universal SDK Blocker` on maintained apps. Stacking adds execution time, runs expensive full-DEX scans, and risks breaking features like push notifications or login if generic toggles collide.
- If a newly discovered generic SDK appears in an onboarded app, author the rule directly inside that app's dedicated patch suite.

### D. Core Patch Typologies
- **`bytecodePatch`**: High-level Dalvik AST transforms via dexlib2 fingerprints and instruction injection (`addInstructions(0, "return-void")`).
- **`resourcePatch`**: XML DOM manipulation (`AndroidManifest.xml`, `res/xml/*.xml`) executed prior to DEX assembly.
- **`rawResourcePatch`**: Deterministic byte-level ELF string redirection, companion `.so` zeroing, or asset binary patching.
- **`universalPatch`**: Generic patches omitting `compatibleWith(...)`, applicable across any target APK without app-specific obfuscation dependencies.

---

## 3. Recon Procedure

### A. Artifact Placement
Place candidate APK, APKM, or XAPK files into `candidate_apks/` or project download search directories. Never commit raw binary APKs to version control.

### B. Badging & Version Metadata Extraction
Extract target package name, version name, version code, and SDK requirements:

```bash
aapt2 dump badging candidate_apks/<app>.apk | grep -E "(package: name=|versionCode=|versionName=|sdkVersion:)"
```

Record the following metadata:
- Package name (e.g. `com.example.android`)
- Version name (e.g. `1.2.3.4`)
- Version code (e.g. `12345678`)
- Minimum SDK (e.g. `26`)
- Supported ABIs (e.g. `arm64-v8a`)

### C. Manifest XML Tree Dump
Dump the decoded manifest tree to a scratch path outside git tracking:

```bash
aapt2 dump xmltree --file AndroidManifest.xml candidate_apks/<app>.apk > scratch/manifest_tree.txt
```

### D. Telemetry Component Inventory
Grep the manifest tree for tracking, advertising, and diagnostic entries:

1. **Permissions (`uses-permission`)**:
   - Advertising ID: `com.google.android.gms.permission.AD_ID`
   - Privacy Sandbox / AdServices:
     - `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
     - `android.permission.ACCESS_ADSERVICES_AD_ID`
     - `android.permission.ACCESS_ADSERVICES_CUSTOM_AUDIENCE`
     - `android.permission.ACCESS_ADSERVICES_TOPICS`
   - Install Referrer: `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE`
   - AppHub / Partner services: `com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE`

2. **Components (`service`, `receiver`, `provider`, `activity`)**:
   - Facebook Analytics: `com.facebook.analytics2.*` (upload services, alarm receivers)
   - Google Measurement / Firebase: `com.google.android.gms.measurement.*`, `com.google.android.gms.analytics.*`
   - Google DataTransport: `com.google.android.datatransport.runtime.*`
   - Device & Install Identifiers: `*FDIDLiteProvider`, `*PhoneIdProvider`, `*InstallReferrerProvider`
   - Firebase Component Discovery: `com.google.firebase.components.ComponentDiscoveryService`
   - MLKit Discovery: `com.google.mlkit.common.internal.MlKitComponentDiscoveryService`

3. **Startup Initializers (`androidx.startup.InitializationProvider`)**:
   - Ad SDK initializers: `com.unity3d.services.core.configuration.AdsSdkInitializer`, `com.google.android.gms.ads.MobileAdsInitProvider`

### E. ML Kit Analysis & Scanner False-Positive Rule
Component scanners (App Manager, Exodus Privacy) frequently flag Google ML Kit components (`MlKitInitProvider`, `MlKitComponentDiscoveryService`) as "trackers" because ML Kit uses the Firebase dependency injection framework (`CommonComponentRegistrar`).
- **Empirical Reality**: ML Kit models (OCR, barcode scanning, Autofill vision) execute strictly locally on-device and send zero telemetry.
- **Rule**: Never strip ML Kit components in default telemetry patches. Disabling ML Kit breaks in-app barcode and QR scanning. If an ML Kit slimmer is desired, author it as a separate opt-in patch with `default = false` and an explicit description warning.

### F. Native Binary Reconnaissance (`lib/<abi>/*.so`)
When the target application bundles native libraries:
1. Inspect bundled native libraries:
   ```bash
   unzip -l candidate_apks/<app>.apk "lib/*"
   ```
2. Scan for embedded telemetry endpoints in native binaries:
   ```bash
   strings -a lib/arm64-v8a/libnative.so | grep -E "(telemetry|analytics|crash|stats|log|metrics)"
   ```
3. Check for standalone crash reporter or profiler `.so` files suitable for companion bloat zeroing (e.g. `libcrashlytics.so`, `libsentry.so`, `libgwp-asan.so`).

### G. Bytecode Scan for Stable SDK Signatures
Scan DEX files with `androguard` to locate stable SDK entrypoints and verify exact Smali descriptors:

```python
# Command run via python virtual environment
./venv/bin/python -c '
from androguard.core.apk import APK
from androguard.core.dex import DEX
apk = APK("candidate_apks/<app>.apk")
for dex_bytes in apk.get_all_dex():
    dex = DEX(dex_bytes)
    for cls in dex.get_classes():
        name = cls.get_name()
        if "analytics" in name.lower() or "measurement" in name.lower():
            for m in cls.get_methods():
                print(f"{name}->{m.get_name()}{m.get_descriptor()}")
' > scratch/sdk_methods.txt
```

Identify stable SDK dispatch points:
- `FirebaseAnalytics.logEvent`: `(Ljava/lang/String; Landroid/os/Bundle;)V` or obfuscated equivalent
- `AppMeasurement.logEventInternal`: `(Ljava/lang/String; Ljava/lang/String; Landroid/os/Bundle;)V`
- App-specific uploader dispatchers: e.g. `IgAnalytics2TaskBasedUploader.HZG` in Instagram

Record exact Smali descriptors:
- Defining class: `Lcom/target/Uploader;`
- Method name: `dispatch`
- Parameter types: `listOf("Ljava/lang/String;", "Landroid/os/Bundle;")`
- Return type: `V` (void) or `Z` (boolean)

### H. Triage Tooling
When signatures shift or cannot be identified via simple grep:
- **`jadx-gui` (Static Triage)**: Open APK in jadx, follow Xrefs from string constants or log messages, and identify the shifted method signature.
- **`frida` / `jnitrace` (Dynamic Lab Triage)**: Attach to the target process on the lab device to confirm whether candidate methods execute during user interactions before writing hooks.

Always store intermediate recon dumps in `scratch/` or `<appDataDir>/brain/<conversation-id>/scratch/`. Never commit raw scan outputs.

---

## 4. Registration & Documentation Deliverables

Onboarding a new target app requires four synchronized deliverables, all committed in the SAME atomic commit as the app's first patch:
1. `Constants.kt` entry
2. `PatchExecutionTest.kt` entry
3. `README.md` Supported Apps table update
4. `docs/apps/<app_id>.md` comprehensive application guide

### A. Centralized Constants (`Constants.kt`)
File: `patches/src/main/kotlin/app/morphe/patches/shared/Constants.kt`

Add package name, single target version, and `Compatibility` contract:

```kotlin
const val <APP>_PACKAGE_NAME = "com.example.android"
const val <APP>_TARGET_VERSION = "1.2.3.4"

val COMPATIBILITY_<APP> = Compatibility(
    name = "<App Name>",
    packageName = <APP>_PACKAGE_NAME,
    apkFileType = ApkFileType.APK, // Use ApkFileType.APKM or ApkFileType.XAPK for bundles
    appIconColor = 0x123456,
    targets = listOf(
        AppTarget(
            version = <APP>_TARGET_VERSION,
            description = "Download com.example.android v$<APP>_TARGET_VERSION (APK) from APKMirror",
        )
    )
)
```

**Single Target Version Rule**: Keep strictly ONE `AppTarget` entry in `targets`.

### B. Runner Registry (`PatchExecutionTest.kt`)
File: `patches/src/main/kotlin/util/PatchExecutionTest.kt`

Add the target to `enum class TargetApp`:

```kotlin
<APP>(
    id = "<app_id>",
    appName = "<App Name>",
    packageName = Constants.<APP>_PACKAGE_NAME,
    candidateFilenames = listOf(
        "<app>_${Constants.<APP>_TARGET_VERSION}.apk",
        "com.example.android_${Constants.<APP>_TARGET_VERSION}.apkm",
    ),
    filePattern = Regex("(?i).*<app_id>.*\\.(?:apk|apkm|xapk)$"),
    patchDirectoryPart = "<app_id>",
),
```

### C. Supported Apps Table (`README.md`)
File: `README.md`

Add the application row to the `## Supported Apps` table:

```markdown
| <App Name> | `<package_name>` | <target_version> |
```

### D. Application Guide Deliverable (`docs/apps/<app_id>.md`)
File: `docs/apps/<app_id>.md`

Every new application onboarding MUST produce a dedicated guide with this exact structure:

```markdown
# <App Name>: Complete Patch & Architecture Guide

Comprehensive technical, architecture, and patch guide for **<App Name>** (`<package_name>`), covering target requirements, telemetry neutralization, and architectural invariants.

---

## Target & Compatibility

| Property | Value |
| :--- | :--- |
| **Target Application** | <App Name> |
| **Package Name** | `<package_name>` |
| **Supported Target Version** | **`<target_version>`** |
| **Target File Format** | Standalone APK (`APK`) or Bundle (`APKM`/`XAPK`) |
| **Recommended Architecture** | `arm64-v8a` |
| **Official Download Source** | [APKMirror / APKPure](url) |

---

## Applied Patches Catalog

| Patch Name | Type | Category | Default | Primary Mechanism |
| :--- | :--- | :--- | :---: | :--- |
| **Block Telemetry & Trackers** | `bytecodePatch` + `resourcePatch` | Privacy & Telemetry | Yes | Strips advertising permissions, disables analytics services/providers in AndroidManifest.xml, and stubs Dalvik telemetry dispatchers. |

---

## Deep Technical Patch Breakdown

### 1. Block Telemetry & Trackers (`<appId>BlockTelemetryPatch`)
- **Objective**: Neutralize tracking SDKs, analytics dispatchers, and advertising permissions.
- **Manifest Purge**: Strips `AD_ID` and advertising permissions, disables measurement and analytics components, and removes discovery registrars.
- **Bytecode Hooks**: Stubs telemetry dispatch methods with early `return-void`.
```

### E. Package Directory
Create the target package directory:
`patches/src/main/kotlin/app/morphe/patches/<app_id>/`

---

## 5. Implementation Skill Delegation

Telemetry blocking and asset debloating implementations are partitioned into dedicated specialized skills. Do not duplicate implementation logic or code snippets across skill definitions.

### A. Telemetry Blocking Implementation (`telemetry-blocking`)
- **When to Invoke**: Invoke when implementing the primary telemetry suppression suite for a target application, including manifest-level permission stripping, component disabling, opt-out metadata injection, and Dalvik bytecode dispatcher stubs.
- **What It Delivers**:
  - Manifest purge implementation via `stripPermissionsWhere`, `disableComponentsByName`, and `setApplicationMetaData`.
  - Component discovery registrar pruning patterns and the ML Kit scanner false-positive caveat.
  - Dalvik bytecode dispatcher stubbing patterns (`return-void`, boolean getters, and asynchronous bridge Promise resolvers).
  - Reverse traversal multi-return hook insertion and register stability invariants.
  - The zero-zombie fingerprint contract and `[Patch Name]` diagnostic telemetry logging standard.
  - Verification assertions against the compiled APK manifest tree via `aapt2`.
  - Reference: `.agents/skills/telemetry-blocking/SKILL.md`.

### B. Application Debloating & Asset Slimming (`app-debloat`)
- **When to Invoke**: Invoke when analyzing or stripping companion native libraries, non-essential asset bundles, multi-language string tables, high-density screen graphics, onboarding videos, editor assets, or background sync schedulers.
- **What It Delivers**:
  - Native binary in-situ zeroing (`file.writeBytes(byteArrayOf())`) for companion AI, VPN, XR, and crash-reporting shared libraries.
  - Localization slimming for Android `res/values-*/` and Chromium DataPack v5 `assets/locales/*.pak` with safe fallback preservation.
  - Screen density (`drawable-*dpi`, `mipmap-*dpi`) and non-phone UI mode trimming with launcher icon protection and orphan asset preservation.
  - Replacement of heavy onboarding media and editor stickers with minimal container headers and transparent PNG stubs.
  - Background wakeup and periodic sync elimination via `BackgroundSyncPurge` patterns.
  - Guidelines for structuring one independent opt-in patch per debloat axis (`default = false`).
  - Reference: `.agents/skills/app-debloat/SKILL.md`.

---

## 6. Verification Gates

### A. Full-Suite In-Situ Patching Gate
Execute Morphe Patcher against the target APK with all patches active:

```bash
# Execute patch test by registered target app id
./gradlew runPatchTest -Papp=<app_id>

# Or with an explicit APK path
./gradlew runPatchTest -Papk=candidate_apks/<app>.apk

# When patch options exist, force all boolean options on
./gradlew runPatchTest -Papp=<app_id> -PallOptions=true
```

#### Quiet Gate Invocation (Agent Standard)
```bash
./gradlew runPatchTest -Papp=<app_id> --console=plain > build/patchtest-<app_id>.log 2>&1; rc=$?; sed -n '/FINAL PATCHING RESULT/,$p' build/patchtest-<app_id>.log | grep -v '^\s*at ' || tail -40 build/patchtest-<app_id>.log; echo "exit=$rc"
```

#### Gate Pass Criteria
- Patcher exit code: `0`
- Failed patches: `0`
- Detected Fingerprint Failures: `0`
- Detected Smali Compile Errors: `0`
- Exceptions: `0`

### B. Lint and Compiler Checks
Run standard project checks:

```bash
./gradlew check
```

### C. Injection Proof via Output APK Inspection
Verify modifications on the actual output APK artifacts rather than trusting stdout counters alone:

```bash
# Generate signed and aligned output APK
./gradlew runPatchTest -Papp=<app_id> -DoutputApk=build/test-<app_id>.apk
```

*Note*: `-DoutputApk` paths work with or without the `.apk` extension per the runner's path normalization logic (e.g. `-DoutputApk=build/test` normalizes to `build/test.apk`).

#### Verification Commands
```bash
# 1. Verify stripped permissions in output APK
aapt2 dump xmltree --file AndroidManifest.xml build/test-<app_id>.apk | grep -E "E: uses-permission.*android:name.*(AD_ID|ACCESS_ADSERVICES)"

# 2. Verify disabled components have android:enabled="false" (0x0)
aapt2 dump xmltree --file AndroidManifest.xml build/test-<app_id>.apk | grep -B 2 -A 5 "AppMeasurementService"

# 3. Verify injected opt-out metadata
aapt2 dump xmltree --file AndroidManifest.xml build/test-<app_id>.apk | grep -B 2 -A 5 "firebase_analytics_collection_enabled"
```

Assert that:
- Blocked permissions are completely absent from the dump.
- Blocked components contain `A: android:enabled(0x0101000e)=0x0`.
- Injected metadata flags are present.

### D. Reverse Engineering Harness Usage (Version Bumps)
For target applications integrated with the automated update harness (`harness/update.py`):
```bash
# Run preflight environment check
./venv/bin/python harness/update.py --doctor

# Non-destructive audit of a new target APK version
./venv/bin/python harness/update.py <path-to-new-apk> --audit
```
*Note*: Applications without a dedicated harness pipeline are audited manually and validated via `./gradlew runPatchTest -Papp=<app_id>`.

---

## 7. Device Validation & Traffic Audit

### A. Automated ADB Smoke Install Gate
Use `validation/smoke_install.py` against a connected Android device matching the target ABI (`arm64-v8a`) and minimum SDK:

```bash
# Single standalone APK
./venv/bin/python validation/smoke_install.py build/test-<app_id>.apk --uninstall-on-conflict

# Explicit device serial if multiple devices are attached
./venv/bin/python validation/smoke_install.py build/test-<app_id>.apk --serial <serial> --uninstall-on-conflict
```

#### Split APKs & APKM Bundles
When testing split APKs extracted from an APKM or XAPK bundle:
- **Critical ADB Constraint**: `adb install-multiple` requires EVERY split file argument to end with the `.apk` extension. Ensure all split files are named `<name>.apk`.

```bash
./venv/bin/python validation/smoke_install.py build/splits/base.apk build/splits/split_config.arm64_v8a.apk --uninstall-on-conflict
```

#### Post-Test Cleanup
Uninstall the test package to leave the device in a clean state:

```bash
adb -s <serial> uninstall <package_name>
```

### B. Physical Device Comparative Validation
For non-trivial telemetry and background sync modifications, perform a comparative run (Vanilla vs Patched) on the physical lab device:
1. Run vanilla APK, measure background jobs and telemetry dispatch.
2. Run patched APK, confirm telemetry endpoints remain silent and app functions with zero crashes.

### C. Runtime Traffic Audit
Perform a runtime packet capture to audit network traffic and verify telemetry suppression:

```bash
# 1. Start packet capture on device
adb -s <serial> shell su -c "tcpdump -i any -s 0 -w /data/local/tmp/traffic.pcap" &
TCPDUMP_PID=$!

# 2. Launch patched app and exercise standard user flows for 60 seconds
adb -s <serial> shell monkey -p <package_name> -c android.intent.category.LAUNCHER 1
sleep 60

# 3. Stop capture and pull pcap file
adb -s <serial> shell su -c "pkill tcpdump"
adb -s <serial> pull /data/local/tmp/traffic.pcap scratch/traffic.pcap
adb -s <serial> shell su -c "rm /data/local/tmp/traffic.pcap"
```

#### Protocol & Host Inventory Extraction
```bash
# Extract TLS Server Name Indication (SNI) hostnames
tshark -r scratch/traffic.pcap -Y "tls.handshake.type == 1" -T fields -e tls.handshake.extensions_server_name | sort -u

# Extract QUIC Initial packet server names (HTTP/3 traffic)
tshark -r scratch/traffic.pcap -Y "quic" -T fields -e quic.tls.handshake.extensions_server_name | sort -u
```

#### Traffic Audit Caveats & Blind Spots
When reporting network audit results, always document technical blind spots:
1. **QUIC / HTTP/3 Traffic**: UDP port 443 traffic bypasses standard HTTP/HTTPS forward proxies unless UDP 443 is blocked or intercepted at the firewall. Inspect QUIC Initial frames directly.
2. **Encrypted Client Hello (ECH)**: TLS 1.3 connections negotiating ECH encrypt the inner SNI, exposing only outer provider hostnames.
3. **DNS-over-TLS (DoT) / DNS-over-HTTPS (DoH)**: Android Private DNS encrypts DNS resolution over port 853 or port 443. Queries do not appear in standard plaintext UDP 53 captures.
4. **Evidence Standard**: Report observed hostnames and explicit caveats. Never claim "100% telemetry blocked" beyond the empirical evidence collected from traffic dumps.

---

## 8. Closing Protocol

### A. Mandatory Atomic Commits & Vertical Slices
Every completed unit of work must be committed immediately as an isolated, independent commit.

1. **Initial App Onboarding Commit (Vertical Slice)**:
   - Scope: Target app registration (`Constants.kt`, `PatchExecutionTest.kt`, `README.md`) + dedicated app guide (`docs/apps/<app_id>.md`) + initial telemetry patch implementation (`patches/.../<app_id>/...`).
   - Format: `feat(<app_id>): add block telemetry patch`
2. **Subsequent Feature / Opt-In Patch Commits**:
   - Scope: Standalone opt-in patch (e.g. MLKit slimmer) + accompanying documentation updates in `docs/apps/<app_id>.md`.
   - Format: `feat(<app_id>): add mlkit vision slimmer patch`
3. **Bugfix Commits**:
   - Scope: Standalone fix for a shifted fingerprint or broken component.
   - Format: `fix(<app_id>): update uploader fingerprint for <version>`

### B. Conventional Commits Standards
- Written strictly in English.
- Imperative mood, concise summary line.
- Zero AI attribution, zero `Co-Authored-By` lines.

### C. Direct Commit & No-Push Invariant
- **Direct Commit**: Commit completed units autonomously upon satisfying verification gates.
- **Strict No-Push**: NEVER execute `git push` autonomously. Pushing is reserved strictly for explicit user instructions.
- **Strict No-PR**: Never generate PR titles, PR descriptions, or suggest opening PRs.
