# Morphe Patches Automated Update & Reverse Engineering Harness

Automated reverse-engineering and patch update harness for **kveld9 Extra Patches** (`com.kveld9.morphe.extra`).

---

## Architecture Overview

The harness automates the reverse-engineering lifecycle when upstream releases new target APKs:

```text
Target APK / APKM / XAPK
     |
[harness/update.py]            -> Package Identification & Pipeline Dispatcher
     |
[harness/core/apk.py]          -> Context-managed Extraction (Version, VersionCode, SHA-256)
     |
[harness/core/dex.py]          -> Multi-DEX Class & Method Indexing
     |
[harness/core/symbols.py]      -> Obfuscated Symbol Resolution (Structural callers, types, opcodes)
     |
[harness/pipelines/<app>]      -> Declarative Target Application Pipeline & Contracts
     |
[harness/migration/validator]  -> Adversarial Validation Engine (DEX, AST, Assets)
     |
[harness/migration/migrator]   -> Minimal Source Update (Constants.kt, Kotlin AST)
     |
[Gradle / Toolchain]           -> check, buildAndroid, MPP bundle integrity
     |
[harness/reporting/]           -> Structured Markdown Reports
```

---

## Daily Operational Procedure

### 0. Preflight Readiness (Fail Fast on Environment Issues)

```bash
python harness/update.py --doctor
```

Checks Python, Androguard, Java, Gradle wrapper, `Constants.kt`, and ADB.
It also reports when optional manual triage aids `jadx` and `frida` are absent;
those never fail the verdict and are never pipeline dependencies.

### Triage Aid: Resolving a BLOCKED Fingerprint (Manual, Optional)

When `--audit` reports `BLOCKED` (0 or ambiguous targets after an upstream rename/obfuscation shift),
these external tools speed up finding the shifted target:

Run `scripts/ensure_lab_frida.sh` to idempotently bootstrap host tools and
ensure `frida-server` is running on the lab device. Availability of both
tools is reflected by `python harness/update.py --doctor`.

1. **jadx** (static): open the APK in jadx-gui, follow Xrefs from the blocking reason's string constant
   or caller, and locate the shifted obfuscated class/method. Then update the fingerprint query in the
   target pipeline and re-run `--audit`.
2. **frida** (dynamic, lab device only): before writing a Smali hook, confirm the candidate actually
   executes as expected. Requires root or Gadget on the attached lab device; the repacked APK smoke test
   stays the faithful verdict.

### 1. Audit a Target APK / APKM (Non-destructive Inspection)

Run this command to inspect fingerprints, obfuscated symbol changes, and invariants without modifying code.
Append `--json` to also emit a machine-readable sidecar (`<output>.json`) with the same content for agent loops.
Point `--output` under `build/` or `/tmp` when the JSON sidecar must not pollute the checkout.

```bash
python harness/update.py <path-to-app.apk> --audit
```

### 2. Update and Build for a New Version

When ready to migrate patches to the new version:

```bash
python harness/update.py <path-to-app.apk> --update
```

This will:
1. Validate all fingerprints, contracts, and safety gates.
2. If all checks pass (`VERIFIED`), apply minimal edits to `Constants.kt` and patch source files.
3. Automatically execute `./gradlew check buildAndroid` and assert MPP bundle integrity, rolling back on failure.
4. Output the complete report.

After any patch source change, the in-situ patching gate is mandatory (see `AGENTS.md`):

```bash
./gradlew runPatchTest -Papp=<targetApp>
```

### 3. Run the Harness Test Suite

```bash
python3 -m unittest discover -s harness/tests
```

---

## Patch Status Definitions

| Status | Definition | Next Action |
| :--- | :--- | :--- |
| **`VERIFIED`** | All fingerprints have exactly 1 target, signatures match, and structural invariants are satisfied. | Ready for release. |
| **`STATICALLY VERIFIED`** | AST/Smali matches verified statically, but optional companion native library was absent. | Review if APK is universal. |
| **`BLOCKED`** | A fingerprint matched 0 targets or was ambiguous (>1 matches). | **HALT.** Inspect structural candidates and update fingerprint. |
| **`NOT AFFECTED`** | Patch is universal or target is unaffected. | No action required. |

---

## Adding a Target Pipeline

When adding a new supported application to `kveld-extra-morphe-patches`:

1. Define `<APP>_PACKAGE_NAME`, `<APP>_TARGET_VERSION`, and `COMPATIBILITY_<APP>` in `Constants.kt`.
2. Create `harness/pipelines/<app>.py` subclassing `BaseTargetPipeline`.
3. Implement `matches_package()`, `validate_apk_sanity()`, `execute_audit_and_validation()`, and `create_migration_plans()`.
4. Register the pipeline in `harness/pipelines/__init__.py`:
   ```python
   from harness.core.pipeline import PipelineRegistry
   from harness.pipelines.<app> import <App>Pipeline
   PipelineRegistry.register(<App>Pipeline)
   ```
