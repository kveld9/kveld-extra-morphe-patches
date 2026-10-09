---
description: Onboard a new app with telemetry-blocking + debloat patches in parallel via AGY
---

## `/telemetry-app` — Parallel Telemetry + Debloat Onboarding

Target: $ARGUMENTS (APK file in `candidate-apks/`, e.g. `candidate-apks/com.example_1.0.apkm`).

You are the AGY orchestrator (skill `agy-orchestrator`; never edit project code
directly, never use another harness's subagents for implementation). Execute:

### Step 0 — Scope bootstrap
1. Record `git status --short`. Isolate any pre-existing dirty files or untracked changes as `FOREIGN_IN_PROGRESS_WORK` (do not require working tree to be clean; never stage or commit foreign files). APK must exist under `candidate-apks/` (gitignored, never commit).
2. Recon (orchestrator shell, read-only): `unzip -l`, badging dump via `${AAPT2:-aapt2}`, manifest xmltree to `$AGY_TMP` (`/tmp/opencode`), telemetry component inventory. Save evidence paths for workers.

### Step 1 — Registration (one AGY worker, `accept-edits`)
New `agy` conversation: add `Constants` entry inside `object Constants` + `TargetApp` entry + README row (matching repository column schema) + `docs/apps/<app_id>.md` skeleton (with mandatory Behavioral Hazards Warning) per `AGENTS.md` section 2. Folding documentation skeleton here avoids Worker A/B collision on docs. Uncommitted; orchestrator commits with the first patch.

### Step 2 — Parallel implementation (TWO AGY workers, exclusive paths)
Launch both in the same turn (background), max two, never the same files:
- Worker A (fresh conversation): skill `telemetry-blocking` → `patches/.../<app>/` telemetry patch (manifest purge + verified DEX hooks only, zero zombies, `[Patch Name]` telemetry contract).
- Worker B (fresh conversation): skill `app-debloat` → independent opt-in slimmer patches (one axis per patch, `default=false`).
- Both: `--add-dir` for project + origin reference repo, no `--dangerously-skip-permissions`, native read/edit only (headless denies RunCommand). Same-scope corrections always resume the same conversation; a failed worker is discarded, never integrated by hand.
- On `RESOURCE_EXHAUSTED`: checkpoint, rotate via `switch_account.py auto` (preauthorized), resume same conversation.

### Step 3 — Gates (orchestrator, writes only to gitignored `build/`)
Run in-situ gates sending output to `build/` (gitignored):
`./gradlew runPatchTest -Papp=<app_id> -Papk=<apk> -PallOptions=true` (100% success, 0 failed patches, 0 fingerprint mismatches, 0 smali compile errors) + `./gradlew check`. Injection proof via output-APK manifest dump (`build/test-<app_id>.apk`), never counters alone.

### Step 4 — Device (standing authorization confirmed)
`validation/smoke_install.py` on ABI-matched device points strictly to fused `build/test-<app_id>.apk`, never source splits (`.apk` extension on every split for `install-multiple`); traffic audit via on-device tcpdump (verifying binary first, packet limit `-c`, non-empty pcap check) with TLS SNI + QUIC Initial inspection; uninstall after. Report hosts + caveats (abort if capture empty, never report false-negative silent telemetry).

### Step 5 — Close
One atomic commit per patch/toggle (`feat(<app>)`/`fix(<app>)`, plain ASCII English, no emojis, no attribution), registration and doc guide folded into the first patch commit. Prior to push, run `audit-stack` pre-push over `origin/<base>..HEAD` (never per-commit auto-audit), verifying `git status` clean of task scope while preserving foreign files. NEVER push or open PRs without explicit user instruction.
