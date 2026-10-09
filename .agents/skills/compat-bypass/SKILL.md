---
name: compat-bypass
description: Technical methodology and implementation patterns for client-side post-patch compatibility blockers, integrity gates, store redirects, and local entitlement bypasses.
---

# Client-Side Compatibility & Functioning Bypass Guidelines

## 1. Scope Boundaries & Core Principles

Client-side compatibility and functioning bypass patches neutralize blockers that prevent a modified APK from installing, launching, or executing its core features after being repackaged, re-signed, or run without proprietary platform dependencies.

### A. Strict Scope Boundaries
- **Permitted Scope**: Patches operate strictly on client-side Dalvik bytecode (`bytecodePatch`), Android XML resources (`resourcePatch`), and local asset files (`rawResourcePatch`).
- **Server-Side Prohibition**: Never attempt to bypass server-side subscription paywalls, unlock cloud-restricted content, access private accounts, defeat DRM protections, or forge remote attestations (authoritative boundary: `docs/out-of-scope.md`, `AGENTS.md` Rule 18).
- **Prohibition on Forging Credentials & Attestations**: Never forge network credentials, session tokens, purchase receipts, or SafetyNet / Play Integrity cryptographic server attestations.
- **Strict Nomenclature Standard**: Describe patch capabilities with technical precision. Use phrases such as "locally", "client-side gate", "UI suppression", and "local entitlement gate". Never document or advertise a patch as an "unlock" or "subscription bypass" when backend servers continue to reject or restrict cloud data or features.
- **Default Policy Invariant**: Set `default = true` ONLY when the complete user flow is verified on a physical device. Any doubt or lack of hardware validation requires `default = false` (opt-in).
- **No Universal Bypasses**: Bypasses are application-specific and must declare explicit targets via centralized `Constants.COMPATIBILITY_<APP>` (`compatibleWith(...)`). Universal bypasses across arbitrary apps are prohibited.
- **Single Target Version Invariant**: Every target application maintains strictly ONE active upstream version. Never retain multi-version fallback code or legacy matrices.

### B. Controlled Scope: Three-Question Feasibility Test
Before implementing any client-side compatibility or entitlement patch, answer all three questions:
1. Is the final decision executed locally by the APK's own bytecode or assets?
2. Does the complete user flow execute entirely on-device?
3. Does the patch operate without forging network credentials, session tokens, purchase receipts, or remote attestations?

**Three-Yes Rule**: All three questions MUST answer YES. If any question is NO, the patch is strictly out of scope and must not be written.

---

## 2. The Four Blocker & Bypass Families

### Family 1: Local Signature & Integrity Verification

#### Signals & Reconnaissance
- APK signature checksum verification at runtime: queries to `PackageManager.getPackageInfo(..., GET_SIGNATURES)` or `GET_SIGNING_CERTIFICATES`.
- Integrity and code transparency callbacks: classes implementing verification callbacks such as `com.microsoft.office.apphost.CodeTransparencyCheckCallback`.
- Anti-tamper and environment detection: heuristics checking for Xposed, Substrate, Frida, root binaries (`/system/bin/su`, `/system/xbin/su`), ADB debugging status, VPN interfaces, or emulator system properties.
- Third-party SDK signature binding: Google Maps Platform SDK verifying certificate fingerprints, causing blank map tiles when re-signed.
- Manifest installation blockers: `android:sharedUserId` declarations causing `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` on modern Android versions when signature differs from shared system packages.

#### Smali & Resource Techniques
1. **Stubbing Integrity Check Methods (Polarity & Direction-Inversion Rule)**:
   Account for check polarity following the direction-inversion trap pattern documented in `telemetry-blocking`:
   - Positive validity assertions (e.g. `isSignatureValid`, `isIntegrityVerified`) must be forced to `0x1` (`true`).
   - Adverse state detectors (e.g. `isRooted`, `isTampered`, `isEmulator`) must be forced to `0x0` (`false`).

   Example forcing a validity assertion (`isSignatureValid -> true`):
   ```kotlin
   Fingerprint(
       definingClass = "Lcom/target/security/IntegrityChecker;",
       name = "isSignatureValid",
       returnType = "Z",
   ).method.apply {
       clearTryBlocks()
       ensureRegisterCount(1)
       implementation?.let { removeInstructions(0, it.instructions.count()) }
       addInstructions(0, """
           const/4 v0, 0x1
           return v0
       """.trimIndent())
   }
   ```
2. **Short-Circuiting Asynchronous Callbacks**:
   Invoke the success callback on parameter registers and return immediately:
   ```kotlin
   codeTransparencyCheckFingerprint.method.apply {
       clearTryBlocks()
       implementation?.let { removeInstructions(0, it.instructions.count()) }
       addInstructions(0, """
           invoke-interface {p2}, Lcom/microsoft/office/apphost/CodeTransparencyCheckCallback;->transparencyVerificationSucceeded()V
           return-void
       """.trimIndent())
   }
   ```
3. **Stripping Manifest `sharedUserId`**:
   Use a `resourcePatch` to strip conflicting attributes from `AndroidManifest.xml`:
   ```kotlin
   document("AndroidManifest.xml").use { doc ->
       val manifest = doc.getElementsByTagName("manifest").item(0) as? Element ?: return@use
       manifest.removeAttribute("android:sharedUserId")
       manifest.removeAttribute("android:sharedUserLabel")
   }
   ```
4. **Signature Spoofing for SDKs**:
   Inject custom API keys via manifest `<meta-data>` or hook PackageManager signature queries at runtime via an extension helper.

#### Anti-Patterns (What NOT to Do)
- NEVER attempt to forge Play Integrity or SafetyNet cryptographic tokens sent to remote application servers.
- NEVER wrap failed checks in broad `try-catch` blocks that swallow runtime exceptions while leaving corrupted state.

#### Verified Codebase Examples
- Microsoft PowerPoint (`kveld-extra-morphe-patches`):
  `patches/src/main/kotlin/app/morphe/patches/powerpoint/BypassCodeTransparencyPatch.kt`
  `patches/src/main/kotlin/app/morphe/patches/powerpoint/RemoveSharedUserIdPatch.kt`
- Xiaomi Earbuds (`brave-origin-patches`):
  `patches/src/main/kotlin/app/morphe/patches/xiaomi/earbuds/XiaomiEarbudsAntiTamperBypassPatch.kt`
- Moovit (`kveld-extra-morphe-patches`):
  `patches/src/main/kotlin/app/morphe/patches/moovit/MoovitFixGoogleMapsPatch.kt`

---

### Family 2: Store-Redirect Killer & Update Nag Suppression

#### Signals & Reconnaissance
- Forced launch of market URLs: `market://details?id=` or `https://play.google.com/store/apps/details?id=` when the app detects an unofficial installation source or version check failure.
- Involuntary welcome or update nag dialogs that block access to main activities.
- Orphan vendor store components: activities and receivers for Huawei AppGallery (`com.huawei.hms.*`), Samsung Galaxy Store (`com.samsung.android.sdk.iap.*`), Xiaomi GetApps (`com.xiaomi.billingclient.*`), RuStore, OneStore, or CafeBazaar.

#### Smali & Resource Techniques
1. **Neutralizing Involuntary Redirect Dispatchers**:
   Replace the launch method or dialog display method with `return-void` or resolve the callback with success:
   ```kotlin
   Fingerprint(
       definingClass = "Lcom/target/updater/UpdateGateActivity;",
       name = "showStoreRedirectDialog",
       returnType = "V",
   ).method.replaceWithReturnVoid()
   ```
2. **Disabling Orphan Store Manifest Components**:
   In `resourcePatch`, disable alternative OEM store activities, services, and receivers using `disableComponentsByName(...)` to prevent runtime crashes when proprietary vendor stores are absent.

#### Anti-Patterns & Critical Billing Caution (What NOT to Do)
- **Preserve Standard Browser & Deep Links**: NEVER strip or modify activity intent filters handling `android.intent.action.VIEW` for HTTP/HTTPS schemes or OAuth login redirection URLs.
- **CRITICAL GOOGLE PLAY BILLING CAUTION**:
  NEVER disable or remove `com.android.billingclient.api.ProxyBillingActivity` or `com.android.billingclient.api.ProxyBillingActivityV2`.
  Disabling Play Billing proxy activities causes unhandled `ActivityNotFoundException` crashes when payment flows are initialized.
  *Caution*: In `brave-origin-patches`, `NokoPrintMultiStoreDebridgerPatch.kt` disabled `ProxyBillingActivity` and `ProxyBillingActivityV2`. DO NOT replicate or propagate this anti-pattern to any patch. Google Play Billing components must always remain enabled.
- Only suppress involuntary, trapped redirects that block legitimate on-device user operations.

#### Verified Codebase Examples
- NokoPrint (`brave-origin-patches`):
  `patches/src/main/kotlin/app/morphe/patches/nokoprint/NokoPrintSkipWelcomeDialogPatch.kt`
  `patches/src/main/kotlin/app/morphe/patches/nokoprint/NokoPrintMultiStoreDebridgerPatch.kt`

---

### Family 3: Client-Side Paywalls & Local Feature Gates

#### Signals & Reconnaissance
- Local boolean entitlement getters: methods such as `isPro()Z`, `isPaying()Z`, `isSubscribed()Z`, `hasFamilyPlan()Z`, `hasPremiumPlan()Z`.
- Subscription package state enums: enums representing account tiers (e.g. `LicensingState->ConsumerPremium`, `SubscriptionPackageState`).
- First-time user experience (FTUX) paywall screens that gate offline editing or utility functions behind subscription modals.
- React Native Hermes bytecode properties in `assets/index.android.bundle`: property lookups for `isPro` or `isPaying`.

#### Smali & Asset Techniques
1. **Stubbing Boolean Feature Getters**:
   Return `0x1` (`true`) for entitlement checks, and `0x0` (`false`) for grace period or payment failure warnings:
   ```kotlin
   hasFamilyPlanFingerprint.method.apply {
       clearTryBlocks()
       ensureRegisterCount(1)
       implementation?.let { removeInstructions(0, it.instructions.count()) }
       addInstructions(0, """
           const/4 v0, 0x1
           return v0
       """.trimIndent())
   }
   ```
2. **Injecting Static Enum Singletons**:
   Return the premium enum instance directly:
   ```kotlin
   getLicensingStateFingerprint.method.apply {
       clearTryBlocks()
       ensureRegisterCount(1)
       implementation?.let { removeInstructions(0, it.instructions.count()) }
       addInstructions(0, """
           sget-object v0, Lcom/microsoft/office/licensing/LicensingState;->ConsumerPremium:Lcom/microsoft/office/licensing/LicensingState;
           return-object v0
       """.trimIndent())
   }
   ```
3. **Short-Circuiting FTUX Task Listeners**:
   Deliver a successful `TaskResult(0)` directly to the task listener interface (`IOnTaskCompleteListener`):
   ```kotlin
   ftuxPaywallLauncherFingerprint.method.apply {
       clearTryBlocks()
       ensureRegisterCount(2)
       implementation?.let { removeInstructions(0, it.instructions.count()) }
       addInstructions(0, """
           new-instance v0, Lcom/microsoft/office/officehub/objectmodel/TaskResult;
           const/4 v1, 0x0
           invoke-direct {v0, v1}, Lcom/microsoft/office/officehub/objectmodel/TaskResult;-><init>(I)V
           invoke-interface {p2, v0}, Lcom/microsoft/office/officehub/objectmodel/IOnTaskCompleteListener;->onTaskComplete(Lcom/microsoft/office/officehub/objectmodel/TaskResult;)V
           return-void
       """.trimIndent())
   }
   ```
4. **Hermes Bytecode Prologue Patching (`rawResourcePatch`)**:
   In React Native Hermes bundles, locate function headers for target properties and overwrite the prologue with `LoadConstTrue r0; Ret r0` (`0x78 0x00 0x5C 0x00`).

#### Anti-Patterns (What NOT to Do)
- NEVER attempt to intercept cloud billing validation APIs or forge server receipts.
- If a feature requires remote cloud computation (e.g. server-side AI generation, cloud file sync), never document it as unlocked.

#### Verified Codebase Examples
- Moovit (`kveld-extra-morphe-patches`):
  `patches/src/main/kotlin/app/morphe/patches/moovit/MoovitUnlockPlusPatch.kt`
  `patches/src/main/kotlin/app/morphe/patches/moovit/MoovitSuppressPaywallsPatch.kt`
- Microsoft PowerPoint (`kveld-extra-morphe-patches`):
  `patches/src/main/kotlin/app/morphe/patches/powerpoint/Unlock365FamilyPatch.kt`
- Hevy (`brave-origin-patches`):
  `patches/src/main/kotlin/app/morphe/patches/hevy/HevyUnlockProPatch.kt`

---

### Family 4: Conditional Guest-Mode & Login Bypass

#### Signals & Reconnaissance
- Compulsory login screens blocking access to offline utilities, device settings, or local hardware controllers (e.g. headphone companion apps, local document editors, local transit navigation).
- Call-site checks evaluating `AccountManager.isLogin()` before opening device settings or performing OTA firmware queries.

#### Smali Techniques
1. **Call-Site Boolean Replacement**:
   Replace `AccountManager.isLogin()` method invocations at specific call sites with `nop` and load constant `1`:
   ```kotlin
   val instructions = implementation?.instructions?.toList() ?: emptyList()
   val targetIdx = instructions.indexOfFirst { ins ->
       val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference
       ref?.definingClass == "Lcom/xiaomi/fitness/account/manager/AccountManager;" && ref.name == "isLogin"
   }
   check(targetIdx >= 0) { "Target invocation AccountManager.isLogin not found" }

   val moveResultIns = instructions[targetIdx + 1]
   val reg = (moveResultIns as OneRegisterInstruction).registerA
   replaceInstruction(targetIdx, "nop")
   replaceInstruction(targetIdx + 1, "const/4 v$reg, 1")
   ```
2. **Setting Persistent FTUX SharedPreferences**:
   Inject instructions into first-run methods to mark the onboarding tutorial or login screen as already completed:
   ```kotlin
   addInstructions(0, """
       invoke-static {}, Lcom/microsoft/office/apphost/OfficeActivityHolder;->GetActivity()Landroid/app/Activity;
       move-result-object v0
       const/4 v1, 0x1
       invoke-static {v0, v1}, Lcom/microsoft/office/officehub/util/OHubSharedPreferences;->setFTUXShown(Landroid/content/Context;Z)V
       return-void
   """.trimIndent())
   ```

#### Strict Exclusions & Null-Identity Crash Guard
1. **HARD EXCLUSION FOR SOCIAL & MESSAGING APPS**:
   Guest-mode or login-bypass patches are STRICTLY PROHIBITED on social networks and messaging platforms (e.g. Discord, Twitter/X, Instagram, Telegram).
   *Rationale*: Social and messaging platforms fundamentally depend on authenticated server identity. Bypassing login locally produces invalid null-session tokens, desynchronized caches, and immediate fatal crashes across network requests.
2. **MANDATORY NULL-IDENTITY CRASH GUARD**:
   In permitted standalone utilities (hardware controllers, offline tools), ensure that stubbed account getters do NOT return `null` if the app subsequently accesses user properties. Return a valid placeholder object or stub individual property getters (e.g. `getUserId() -> "guest"`) to prevent unhandled `NullPointerException` crashes.

#### Verified Codebase Examples
- Xiaomi Earbuds (`brave-origin-patches`):
  `patches/src/main/kotlin/app/morphe/patches/xiaomi/earbuds/XiaomiEarbudsGuestOtaUnlockPatch.kt`
- Microsoft PowerPoint (`kveld-extra-morphe-patches`):
  `patches/src/main/kotlin/app/morphe/patches/powerpoint/DisableLoginRequirementPatch.kt`

---

## 3. Mandatory Engineering Contracts

Every compatibility and functioning bypass patch must comply with the following architectural invariants:

### A. Zero-Zombie Fingerprint Contract
- Every fingerprint in committed code MUST resolve cleanly against the target APK.
- Wrapping hooks in `try-catch` blocks is permitted strictly as a temporary local diagnostic aid during initial triage. Leaving caught fingerprint mismatches in committed code is strictly prohibited.
- When an upstream application update removes a class, method, or gate, prune the obsolete fingerprint entirely.

### B. Clean Bytecode Replacement (`clearTryBlocks` & `ensureRegisterCount`)
When replacing a method implementation entirely (`removeInstructions(0, count)`):
- Always call `clearTryBlocks()` before removing instructions. Failing to clear try blocks leaves dangling exception handler ranges that trigger Dalvik/ART `VerifyError` at runtime.
- Always call `ensureRegisterCount(N)` to allocate sufficient registers for the injected code.
- Example pattern:
  ```kotlin
  targetFingerprint.method.apply {
      clearTryBlocks()
      ensureRegisterCount(2)
      implementation?.let { removeInstructions(0, it.instructions.count()) }
      addInstructions(0, """
          const/4 v0, 0x1
          return v0
      """.trimIndent())
  }
  ```

### C. Register Stability & Smali Range Invariant
- Non-range invoke instructions (`invoke-virtual`, `invoke-static`, `invoke-interface`, `invoke-direct`) can address ONLY registers `v0` through `v15`.
- In methods with high register counts (e.g. 18+ registers), parameter registers `p0`, `p1`, etc., map to high register indices (`v16`, `v17`).
- Attempting to pass high registers into non-range invokes causes the smali compiler to silently drop the invalid instruction, resulting in broken logic.
- Always use `invoke-*/range` or copy high registers into low registers (`v0`-`v15`) before calling methods.

### D. Surgical Diagnostic Telemetry Standard
Every patch execution must emit clean, high-signal diagnostic output:
- **Prefix**: Every log line begins with the bracketed patch name: `println("[<Patch Name>] ...")`.
- **Dynamic Mutation Counter**: Track applied hooks with local counters (`var patched = 0`).
- **Consolidated Summary**: Emit a single concluding summary line:
  `println("[<Patch Name>] Applied $patched hooks -> client-side checks bypassed.")`
- **Guard Transparency**: Log explicit descriptive reasons when skipping execution:
  `println("[<Patch Name>] Skipped: AndroidManifest.xml not found.")`
- **Zero Loop Spam**: Never log inside iteration loops. Aggregate counts and emit single totals.
- **Plain ASCII Only**: Never use emojis, unicode pictographs, or non-ASCII characters in log output.

### E. Mandatory Preservation Invariants
- NEVER strip `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`.
- NEVER disable push notification services or receivers (`com.google.firebase.messaging.FirebaseMessagingService`, etc.).
- NEVER strip activity intent filters handling `android.intent.action.VIEW`.
- NEVER disable `com.android.billingclient.api.ProxyBillingActivity` or `ProxyBillingActivityV2`.

---

## 4. Verification & Testing Protocol

### A. Manifest Verification (`aapt2 dump xmltree`)
Verify that manifest-level transformations were correctly applied to the output APK:
```bash
# Verify sharedUserId was stripped
aapt2 dump xmltree --file AndroidManifest.xml build/test-<app>.apk | grep "android:sharedUserId"

# Verify orphan store components are disabled
aapt2 dump xmltree --file AndroidManifest.xml build/test-<app>.apk | grep -B 2 -A 5 "AppStoreRustoreActivity"
```

### B. In-Situ Morphe Patcher Verification Gate
Run the official patching harness against the candidate APK with all patches and options activated:
```bash
./gradlew runPatchTest -Papp=<app_id> -PallOptions=true
```
The patching run MUST complete with:
- Exit code: `0`
- Failed patches: `0`
- Detected Fingerprint Failures: `0`
- Detected Smali Compile Errors: `0`
- Exceptions: `0`

### C. Physical Device Comparative Validation
For compatibility bypass patches, perform a comparative run on the physical lab device:
1. **Vanilla Baseline**: Install vanilla APK, exercise the target flow, and document default gate, login, or licensing behavior.
2. **Patched Execution**: Install patched APK, execute the flow, and verify that gates open cleanly without crashes.
3. **State Persistence & Cold Restart Verification**:
   - Force-stop the application (`adb shell am force-stop <package>`) and relaunch from the launcher.
   - Evict the process from the recent tasks list.
   - Clear application cache (`adb shell pm trim-caches 100M`) and reboot device if necessary.
   - Assert that the app remains in the unlocked/bypassed state, does not revert to locked screens, and exhibits zero `NullPointerException` crashes.
