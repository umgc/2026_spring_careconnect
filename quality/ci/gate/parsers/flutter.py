"""
Flutter Analyze Parser (Dart Static Analyzer)

Purpose
-------
Parse the Flutter or Dart analyzer JSON artifact and normalize
findings into the standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/flutter_analyze.json

Native Flutter or Dart Analyzer Severities
------------------------------------------
error
    Compile-time or analysis error. Build will fail.
warning
    Potential issue. Build may still succeed.
info
    Informational message at a low enforcement level.
hint
    Style or best-practice suggestion.

Severity Mapping
----------------
Flutter -> Normalized

- error -> high
- warning -> medium
- info -> low
- hint -> info
- unknown -> info

Behavior
--------
- Reads the structured issues array written by the CI workflow step.
- Maps native severity to normalized severity.
- Populates findings with per-issue detail.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Does not apply policy thresholds.

Expected Artifact Structure
---------------------------
{
  "issues": [
    {
      "severity": "error",
      "message": "The method 'login' isn't defined.",
      "file": "lib/auth/login.dart",
      "line": 34,
      "column": 1,
      "rule": "undefined_method"
    }
  ]
}
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "error": "high",
    "warning": "medium",
    "info": "low",
    "hint": "info",
}


def parse_flutter(raw_dir: Path) -> dict:
    """
    Parse flutter_analyze.json and return a standardized result dictionary.

    Parameters
    ----------
    raw_dir : Path
        Directory containing raw tool output artifacts.

    Returns
    -------
    dict
        Result dictionary conforming to the base_tool_result schema,
        including findings, severity counts, and max_severity.

    Contract
    --------
    - Always returns a base_tool_result structure.
    - Never raises exceptions outward.
    - Missing artifact sets artifact_present=False and runtime_error=True.
    - Malformed JSON sets runtime_error=True and records the error in metadata.
    - Empty issues are treated as a valid execution with zero violations.
    """
    tool_name = "flutter_analyze"
    result = base_tool_result(tool_name)
    artifact = raw_dir / "flutter_analyze.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            data = json.load(file_handle)

        issues = data.get("issues", [])
        findings = []

        for issue in issues:
            native_severity = issue.get("severity", "info").lower()
            normalized_severity = SEVERITY_MAP.get(native_severity, "info")
            result["severity_counts"][normalized_severity] += 1

            finding = {
                "file": issue.get("file", "unknown"),
                "line": issue.get("line", 0),
                "column": issue.get("column", 0),
                "severity": normalized_severity,
                "native_severity": native_severity,
                "message": issue.get("message", ""),
                "rule": issue.get("rule", "unknown"),
            }
            findings.append(finding)

        result["findings"] = findings
        result["violation_count"] = len(findings)
        result["max_severity"] = determine_max_severity(result["severity_counts"])

    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Flutter parse error: {error}"

    return result
