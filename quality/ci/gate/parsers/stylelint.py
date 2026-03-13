"""
Stylelint Parser (CSS/SCSS Static Analysis)

Purpose
-------
Parse Stylelint JSON output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/stylelint.json

Native Stylelint Severities
---------------------------
error
    Rule violation that must be fixed.
warning
    Advisory violation.

Severity Mapping
----------------
Stylelint -> Normalized

- error -> high
- warning -> medium
- unknown -> low

Stylelint JSON Structure
------------------------
[
  {
    "source": "frontend/web/styles/main.css",
    "warnings": [
      {
        "rule": "color-no-invalid-hex",
        "text": "Unexpected invalid hex color \"#gggggg\"",
        "severity": "error",
        "line": 12,
        "column": 10,
        "url": "https://stylelint.io/user-guide/rules/color-no-invalid-hex"
      }
    ]
  }
]

Note
----
Stylelint uses the "warnings" array for all findings regardless of
severity. The actual severity is stored in the "severity" field of
each warning object.
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "error": "high",
    "warning": "medium",
}


def parse_stylelint(raw_dir: Path) -> dict:
    """
    Parse stylelint.json and return a standardized result dictionary.

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
    result = base_tool_result("stylelint")
    artifact = raw_dir / "stylelint.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            data = json.load(file_handle)

        file_results = data if isinstance(data, list) else []
        findings = []

        for file_result in file_results:
            file_path = file_result.get("source", "unknown")
            warnings = file_result.get("warnings", [])

            for warning in warnings:
                native_sev = (warning.get("severity") or "warning").lower()
                norm_sev = SEVERITY_MAP.get(native_sev, "low")
                result["severity_counts"][norm_sev] += 1

                findings.append(
                    {
                        "file": file_path,
                        "line": warning.get("line", 0),
                        "column": warning.get("column", 0),
                        "severity": norm_sev,
                        "native_severity": native_sev,
                        "rule": warning.get("rule", "unknown"),
                        "message": warning.get("text", ""),
                        "rule_url": warning.get("url", ""),
                    }
                )

        result["findings"] = findings
        result["violation_count"] = len(findings)
        result["max_severity"] = determine_max_severity(result["severity_counts"])

    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Stylelint parse error: {error}"

    return result
