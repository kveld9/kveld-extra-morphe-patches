"""
Unit Tests for Migrator, Symbols, and Validator.
Verifies version bump updates, no-op identical version handling, symbol mappings, and patch status.
"""

import tempfile
import unittest
from pathlib import Path
from harness.migration.patch_migrator import PatchMigrator, MigrationPlan
from harness.core.symbols import ResolvedSymbol, SymbolConfidence
from harness.migration.validator import PatchStatus, PatchAuditResult


class TestMigratorAndValidator(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_root = Path(self.temp_dir.name)
        self.constants_file = self.repo_root / "patches/src/main/kotlin/app/morphe/patches/shared/Constants.kt"
        self.constants_file.parent.mkdir(parents=True, exist_ok=True)
        self.constants_file.write_text('const val TARGET_VERSION = "1.0.0"\n', encoding="utf-8")
        self.migrator = PatchMigrator(self.repo_root)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_version_new_metadata_updated(self):
        plan = self.migrator.plan_version_constant_update("TARGET_VERSION", "1.1.0")
        self.assertTrue(plan.has_changes)
        self.assertIn('const val TARGET_VERSION = "1.1.0"', plan.modified_content)
        self.assertEqual(plan.changes, ["Updated TARGET_VERSION to '1.1.0'"])

    def test_version_identical_noop(self):
        plan = self.migrator.plan_version_constant_update("TARGET_VERSION", "1.0.0")
        self.assertFalse(plan.has_changes)
        self.assertEqual(len(plan.changes), 0)

    def test_symbol_resolved_verified(self):
        sym = ResolvedSymbol(
            symbol_id="test_field",
            target_class="Lcom/test/Target;",
            old_symbol="a:Z",
            new_symbol="b:Z",
            symbol_type="field",
            confidence=SymbolConfidence.VERIFIED,
            evidence=["Instance boolean field found"],
        )
        self.assertEqual(sym.confidence, SymbolConfidence.VERIFIED)
        self.assertEqual(sym.new_symbol, "b:Z")

    def test_symbol_blocked(self):
        sym = ResolvedSymbol(
            symbol_id="test_field",
            target_class="Lcom/test/Target;",
            old_symbol="a:Z",
            new_symbol="UNKNOWN",
            symbol_type="field",
            confidence=SymbolConfidence.BLOCKED,
            evidence=["No matching boolean field"],
        )
        self.assertEqual(sym.confidence, SymbolConfidence.BLOCKED)

    def test_patch_not_affected(self):
        audit_res = PatchAuditResult(
            patch_name="Universal Patch",
            status=PatchStatus.NOT_AFFECTED,
            fingerprint_results=[],
            native_checks=[],
            blocking_reasons=[],
            evidence=["Patch is universal and has no package-specific targets."],
        )
        self.assertEqual(audit_res.status, PatchStatus.NOT_AFFECTED)


if __name__ == "__main__":
    unittest.main()
