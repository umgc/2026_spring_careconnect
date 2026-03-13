"""
Pylint Parser (Python Static Analysis)

Purpose
-------
Parse Pylint JSON output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/pylint.json

Native Pylint Message Types
---------------------------
fatal
    Pylint itself crashed on this file.
error
    Code error and likely bug.
warning
    Potential issue.
convention
    Style violation.
refactor
    Refactoring suggestion.

Severity Mapping
----------------
Pylint -> Normalized

- fatal -> critical
- error -> high
- warning -> medium
- refactor -> low
- convention -> low
- unknown -> info

Pylint JSON Structure
---------------------
[
  {
    "type": "error",
    "module": "quality.ci.gate.normalize",
    "path": "quality/ci/gate/normalize.py",
    "line": 42,
    "column": 4,
    "symbol": "undefined-variable",
    "message": "Undefined variable 'foo'"
  }
]
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "fatal": "critical",
    "error": "high",
    "warning": "medium",
    "refactor": "low",
    "convention": "low",
}


def parse_pylint(raw_dir: Path) -> dict:
    """
    Parse pylint.json and return a standardized result dictionary.

    Parameters
    ----------
    raw_dir : Path
        Directory containing raw tool output artifacts.

    Returns
    -------
    dict
        Result dictionary conforming to the base_tool_result schema.

    Contract
    --------
    - Always returns a base_tool_result structure.
    - Never raises exceptions outward.
    - Missing artifact sets artifact_present=False and runtime_error=True.
    - Malformed JSON sets runtime_error=True and records the error in metadata.
    - Empty array is treated as a valid execution with zero violations.
    """
    result = base_tool_result("pylint")
    artifact = raw_dir / "pylint.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            data = json.load(file_handle)

        raw_findings = data if isinstance(data, list) else []
        findings = []

        for raw in raw_findings:
            native_sev = (raw.get("type") or "warning").lower()
            norm_sev = SEVERITY_MAP.get(native_sev, "info")
            result["severity_counts"][norm_sev] += 1

            findings.append(
                {
                    "file": raw.get("path", "unknown"),
                    "line": raw.get("line", 0),
                    "column": raw.get("column", 0),
                    "severity": norm_sev,
                    "native_severity": native_sev,
                    "rule": raw.get("symbol", "unknown"),
                    "message": raw.get("message", ""),
                }
            )

        result["findings"] = findings
        result["violation_count"] = len(findings)
        result["max_severity"] = determine_max_severity(result["severity_counts"])

    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Pylint parse error: {error}"

    return result
