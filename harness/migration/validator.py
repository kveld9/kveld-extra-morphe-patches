"""
Adversarial Validation Engine for Morphe Patches.
Executes structural uniqueness assertions, Gradle toolchain builds, and bundle integrity checks.
"""

from __future__ import annotations

import subprocess
import sys
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import List, Optional, Tuple

from harness.core.dex import DexIndex
from harness.core.elf import Elf64Analyzer
from harness.core.fingerprints import FingerprintQuery, FingerprintResolver, FingerprintStatus


class PatchStatus(str, Enum):
    VERIFIED = "VERIFIED"
    STATICALLY_VERIFIED = "STATICALLY VERIFIED"
    BLOCKED = "BLOCKED"
    NOT_AFFECTED = "NOT AFFECTED"


@dataclass
class PatchAuditResult:
    patch_name: str
    status: PatchStatus
    fingerprint_results: List[Tuple[str, str, str]] = field(default_factory=list)  # (id, status, details)
    native_checks: List[Tuple[str, bool, str]] = field(default_factory=list)  # (target, passed, note)
    blocking_reasons: List[str] = field(default_factory=list)
    evidence: List[str] = field(default_factory=list)


class AdversarialValidator:
    """Rigorous validator ensuring zero false-positives and complete reproducibility."""

    def __init__(self, repo_root: str | Path, dex_index: DexIndex, elf_analyzer: Optional[Elf64Analyzer] = None):
        self.repo_root = Path(repo_root).resolve()
        self.dex_index = dex_index
        self.elf_analyzer = elf_analyzer
        self.fp_resolver = FingerprintResolver(dex_index)

    def resolve_queries(self, queries: List[FingerprintQuery]) -> Tuple[List[Tuple[str, str, str]], List[str], List[str]]:
        """Resolves each query, returning (fingerprint_results, blocking_reasons, evidence)."""
        return self._resolve_queries(queries)

    def _resolve_queries(self, queries: List[FingerprintQuery]) -> Tuple[List[Tuple[str, str, str]], List[str], List[str]]:
        """Internal resolver returning (fingerprint_results, blocking_reasons, evidence)."""
        fp_res, blocking, evidence = [], [], []
        for q in queries:
            res = self.fp_resolver.resolve(q)
            fp_res.append((q.name_id, res.status.value, res.matched_method.full_name if res.matched_method else "NONE"))
            if res.status != FingerprintStatus.VERIFIED:
                blocking.append(f"Fingerprint '{q.name_id}' failed: {res.status.value}")
            else:
                evidence.extend(res.evidence)
        return fp_res, blocking, evidence

    def run_gradle_build_verification(self) -> Tuple[bool, str]:
        """Runs gradle check and buildAndroid, then validates .mpp bundle integrity.

        patches-list.json and README patch tables are release artifacts managed by CI.
        """
        gradle_cmd = str(self.repo_root / ("gradlew.bat" if sys.platform.startswith("win") else "gradlew"))
        cmd = [gradle_cmd, "check", "buildAndroid"]
        res = subprocess.run(cmd, cwd=str(self.repo_root), capture_output=True, text=True, shell=sys.platform.startswith("win"))
        if res.returncode != 0:
            return False, f"Gradle build failed:\n{res.stdout}\n{res.stderr}"

        bundle_ok, bundle_err = self.assert_mpp_bundle_integrity()
        if not bundle_ok:
            return False, bundle_err

        return True, "Gradle check, buildAndroid, and MPP bundle integrity verification passed."

    def assert_mpp_bundle_integrity(self) -> Tuple[bool, str]:
        """Asserts that the compiled .mpp bundle contains classes.dex (Dalvik bytecode) and required extensions.
        Prevents shipping bundles without Android DEX that cause 'Parches: 0' in Morphe Manager.
        """
        import zipfile
        libs_dir = self.repo_root / "patches" / "build" / "libs"
        if not libs_dir.exists():
            return False, f"Build output directory does not exist: {libs_dir}"

        mpp_files = [f for f in libs_dir.glob("*.mpp") if not f.name.endswith("-sources.mpp") and not f.name.endswith("-javadoc.mpp")]
        if not mpp_files:
            return False, f"No .mpp bundle found in {libs_dir}. Ensure 'buildAndroid' was executed."

        for mpp in mpp_files:
            try:
                with zipfile.ZipFile(mpp, "r") as zf:
                    namelist = zf.namelist()
                    if "classes.dex" not in namelist:
                        return False, (
                            f"CRITICAL ERROR in {mpp.name}: 'classes.dex' is MISSING! "
                            f"Morphe Manager on Android requires Dalvik bytecode to load patches. "
                            f"Running standard 'gradle build' only creates Java .class files. "
                            f"You MUST always execute 'gradle buildAndroid' to invoke D8 and package classes.dex."
                        )
                    dex_size = zf.getinfo("classes.dex").file_size
                    if dex_size < 1024:
                        return False, f"CRITICAL ERROR in {mpp.name}: 'classes.dex' is abnormally small ({dex_size} bytes)."

                    if "extensions/extension.mpe" not in namelist:
                        return False, f"CRITICAL ERROR in {mpp.name}: 'extensions/extension.mpe' is MISSING."
            except Exception as e:
                return False, f"Failed to inspect {mpp.name}: {e}"

        return True, "MPP bundle integrity verified (classes.dex and extensions/extension.mpe present)."

    def assert_patches_dynamic_logging(self) -> Tuple[bool, str]:
        """Asserts that all Kotlin patch definitions in patches/.../*.kt contain dynamic diagnostic logging.
        Ensures patches emit concise runtime telemetry for debugging/diagnosis rather than running silently.
        """
        patches_dir = self.repo_root / "patches" / "src" / "main" / "kotlin" / "app" / "morphe" / "patches"
        if not patches_dir.exists():
            return False, f"Patches directory not found: {patches_dir}"

        kt_files = [
            f for f in patches_dir.rglob("*.kt")
            if not (f.parent.name == "shared" and not f.name.endswith("Patch.kt"))
            and f.name != "SharedExtensionPatch.kt"
        ]
        missing_logs = []
        verified_count = 0
        for kt in kt_files:
            content = kt.read_text(encoding="utf-8")
            # Declaration-only files (e.g. Fingerprints.kt) cannot emit patch-time telemetry
            # by construction; enforcement on patch definitions is unchanged.
            if not any(m in content for m in ("bytecodePatch(", "resourcePatch(", "rawResourcePatch(")):
                continue
            verified_count += 1
            if "println(" not in content:
                missing_logs.append(str(kt.relative_to(self.repo_root)))

        if missing_logs:
            return False, "The following patch files are missing diagnostic logging:\n" + "\n".join(missing_logs)

        return True, f"All {verified_count} patch definitions have verified diagnostic logging."
