---
description: Onboard a new app with telemetry-blocking + debloat patches in parallel via AGY
---

## `/telemetry-app` — Parallel Telemetry + Debloat Onboarding

Target: $ARGUMENTS (APK file in `candidate-apks/`, e.g. `candidate-apks/com.example_1.0.apkm`).

You are the AGY orchestrator (skill `agy-orchestrator`; never edit project code
directly, never use another harness's subagents for implementation). Execute:

### Step 0 — Scope bootstrap
1. `git status --short` must be clean; record it. APK must exist under `candidate-apks/` (gitignored, never commit).
2. Recon (orchestrator shell, read-only): `unzip -l`, `aapt2 dump badging`, manifest xmltree to `$AGY_TMP` (`/tmp/opencode`), telemetry component inventory. Save evidence paths for workers.

### Step 1 — Registration (one AGY worker, `accept-edits`)
New `agy` conversation: add `Constants` entry + `TargetApp` entry + README row
per `AGENTS.md` section 2. Uncommitted; orchestrator commits with the first patch.

### Step 2 — Parallel implementation (TWO AGY workers, exclusive paths)
Launch both in the same turn (background), max two, never the same files:
- Worker A (fresh conversation): skill `telemetry-blocking` → `patches/.../<app>/` telemetry patch (manifest purge + verified DEX hooks only, zero zombies, `[Patch Name]` telemetry contract).
- Worker B (fresh conversation): skill `app-debloat` → independent opt-in slimmer patches (one axis per patch, `default=false`).
- Both: `--add-dir` for project + origin reference repo, no `--dangerously-skip-permissions`, native read/edit only (headless denies RunCommand). Same-scope corrections always resume the same conversation; a failed worker is discarded, never integrated by hand.
- On `RESOURCE_EXHAUSTED`: checkpoint, rotate via `switch_account.py auto` (preauthorized), resume same conversation.

### Step 3 — Gates (orchestrator, writes only to gitignored `build/`)
`./gradlew runPatchTest -Papk=<apk>` (100%, 0 mismatches, 0 smali errors) + `-PallOptions=true` path + `./gradlew check`. Injection proof via output-APK manifest dump, never counters alone.

### Step 4 — Device (standing authorization confirmed)
`validation/smoke_install.py` on ABI-matched device (`.apk` extension on every split for `install-multiple`); traffic audit via on-device tcpdump with TLS SNI + QUIC Initial decryption; uninstall after. Report hosts + caveats.

### Step 5 — Close
One atomic commit per patch/toggle (`feat(<app>)`/`fix(<app>)`, English, no attribution), registration folded into the first patch commit. Verify `git status` clean. NEVER push or open PRs without explicit user instruction.
