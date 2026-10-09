"""
Minimal Patch Source Code Migrator.
Performs minimal, surgical regex-anchored source updates on target constants in Constants.kt.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import List, Tuple


@dataclass
class MigrationPlan:
    file_path: Path
    original_content: str
    modified_content: str
    changes: List[str]

    @property
    def has_changes(self) -> bool:
        return self.original_content != self.modified_content


class PatchMigrator:
    """Calculates and applies minimal source modifications to Kotlin patch files and constants."""

    def __init__(self, repo_root: str | Path):
        self.repo_root = Path(repo_root).resolve()
        self.constants_file = self.repo_root / "patches/src/main/kotlin/app/morphe/patches/shared/Constants.kt"

    def plan_version_constant_update(self, const_name: str, new_version: str) -> MigrationPlan:
        """Bumps a single `const val <NAME> = "..."` entry without modifying other lines."""
        return self.plan_version_constants_update([(const_name, new_version)])

    def plan_version_constants_update(self, updates: List[Tuple[str, str]]) -> MigrationPlan:
        """Bumps multiple `const val <NAME> = "..."` entries without touching any other line."""
        if not self.constants_file.exists():
            return MigrationPlan(self.constants_file, "", "", [])

        content = self.constants_file.read_text(encoding="utf-8")
        new_content = content
        changes = []
        for const_name, new_version in updates:
            updated = re.sub(
                rf'const val {const_name} = "[^"]+"',
                f'const val {const_name} = "{new_version}"',
                new_content,
                count=1,
            )
            if updated != new_content:
                changes.append(f"Updated {const_name} to '{new_version}'")
                new_content = updated
        return MigrationPlan(self.constants_file, content, new_content, changes)

    def apply_plan(self, plan: MigrationPlan):
        if plan.has_changes:
            plan.file_path.write_text(plan.modified_content, encoding="utf-8")
