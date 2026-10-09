"""
Unit Tests for Harness Reporting Architecture.
Verifies consistent, deterministic markdown and JSON report generation.
"""

import json
import unittest
from harness.reporting.reporter import HarnessReporter, HarnessReportData
from harness.migration.validator import PatchAuditResult, PatchStatus


class TestHarnessReporting(unittest.TestCase):

    def test_report_markdown_and_json_rendering(self):
        data = HarnessReportData(
            app_name="Test Application",
            package_name="com.test.app",
            mode="AUDIT",
            old_version="1.0.0",
            new_version="1.1.0",
            old_version_code=100,
            new_version_code=110,
            apk_sha256="abc123sha",
            apk_file_size=50000000,
            patch_results={
                "testPatch": PatchAuditResult(
                    patch_name="Test Patch",
                    status=PatchStatus.VERIFIED,
                    fingerprint_results=[("test_fp", "VERIFIED", "Lcom/test/Target;->run()V")],
                    native_checks=[("test.host.com", True, "Offset 0x1000")],
                    blocking_reasons=[],
                    evidence=["Unique match"],
                )
            },
            applied_changes=[],
            rejected_changes=[],
            build_passed=True,
            build_output="Build skipped in audit mode.",
            final_status="SUCCESS",
        )

        md = HarnessReporter.render_markdown(data)
        self.assertIn("# Test Application Patches Harness Report", md)
        self.assertIn("- **Execution Mode**: `[AUDIT]`", md)
        self.assertIn("- **Overall Pipeline Status**: [PASS] `SUCCESS`", md)
        self.assertIn("- **Package Name**: `com.test.app`", md)
        self.assertIn("| **Test Patch** | [PASS] `VERIFIED` | `1/1 verified` | `1/1 valid` | All structural assertions satisfied. |", md)
        self.assertIn("**Final Pipeline Status**: `SUCCESS`", md)

        json_str = HarnessReporter.render_json(data)
        parsed = json.loads(json_str)
        self.assertEqual(parsed["app_name"], "Test Application")
        self.assertEqual(parsed["package_name"], "com.test.app")
        self.assertEqual(parsed["final_status"], "SUCCESS")


if __name__ == "__main__":
    unittest.main()
