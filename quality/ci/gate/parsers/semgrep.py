"""
Semgrep Parser (Multi-language SAST)

Purpose
-------
Parse Semgrep JSON output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/semgrep.json

Native Semgrep Severities
-------------------------
ERROR
    Rule matched a high-confidence security issue.
WARNING
    Rule matched a potential issue with lower confidence.
INFO
    Informational match at the lowest enforcement level.
INVENTORY
    Inventory or audit finding rather than a direct vulnerability.

Severity Mapping
----------------
Semgrep -> Normalized

- ERROR -> high
- WARNING -> medium
- INFO -> low
- INVENTORY -> info
- unknown -> info

Note
----
Semgrep does not emit a native critical severity. High is the
maximum mapped level.

Behavior
--------
- Reads the "results" array from the Semgrep JSON artifact.
- Maps native severity to normalized severity.
- Populates findings with per-finding detail including CWE and OWASP.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Does not apply policy thresholds.

Semgrep JSON Structure
----------------------
{
  "results": [
    {
      "check_id": "python.flask.security.injection.tainted-sql-string",
      "path": "src/main/app.py",
      "start": { "line": 42, "col": 5 },
      "end": { "line": 42, "col": 30 },
      "extra": {
        "severity": "ERROR",
        "message": "Possible SQL injection...",
        "metadata": {
          "cwe": ["CWE-89: Improper Neutralization..."],
          "owasp": ["A1:2017 - Injection"]
        }
      }
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
    "inventory": "info",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_semgrep_data(artifact: Path, result: dict) -> dict | None:
    """
    Load the Semgrep JSON artifact safely.

    Parameters
    ----------
    artifact : Path
        Path to semgrep.json.
    result : dict
        Result dictionary updated on load failure.

    Returns
    -------
    dict | None
        Parsed JSON document or None on failure.
    """
    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            return json.load(file_handle)
    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Semgrep parse error: {error}"
        return None


def _normalize_to_list(value: list | str | None) -> list:
    """
    Normalize a Semgrep metadata field to a list.

    Parameters
    ----------
    value : list | str | None
        Metadata field value.

    Returns
    -------
    list
        Normalized list value.
    """
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return value
    return []


def _extract_semgrep_details(raw_finding: dict) -> tuple[dict, dict, dict]:
    """
    Extract nested Semgrep dictionaries safely.

    Parameters
    ----------
    raw_finding : dict
        Raw Semgrep result record.

    Returns
    -------
    tuple[dict, dict, dict]
        Extra data, metadata, and start position dictionaries.
    """
    extra_data = raw_finding.get("extra", {})
    if not isinstance(extra_data, dict):
        extra_data = {}

    metadata = extra_data.get("metadata", {})
    if not isinstance(metadata, dict):
        metadata = {}

    start_data = raw_finding.get("start", {})
    if not isinstance(start_data, dict):
        start_data = {}

    return extra_data, metadata, start_data


def _build_semgrep_finding(raw_finding: dict) -> tuple[dict, str]:
    """
    Convert one Semgrep result into a normalized finding.

    Parameters
    ----------
    raw_finding : dict
        Raw Semgrep result record.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    extra_data, metadata, start_data = _extract_semgrep_details(raw_finding)

    native_severity = extra_data.get("severity", "INFO").lower()
    normalized_severity = SEVERITY_MAP.get(native_severity, "info")

    finding = {
        "file": raw_finding.get("path", "unknown"),
        "line": start_data.get("line", 0),
        "column": start_data.get("col", 0),
        "severity": normalized_severity,
        "native_severity": native_severity.upper(),
        "rule": raw_finding.get("check_id", "unknown"),
        "message": extra_data.get("message", ""),
        "cwe": _normalize_to_list(metadata.get("cwe", [])),
        "owasp": _normalize_to_list(metadata.get("owasp", [])),
    }

    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_semgrep(raw_dir: Path) -> dict:
    """
    Parse Semgrep JSON and return a standardized result dictionary.

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
    - Empty results are treated as a valid execution with zero violations.
    """
    result = base_tool_result("semgrep")
    artifact = raw_dir / "semgrep.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    data = _load_semgrep_data(artifact, result)
    if data is None:
        return result

    raw_findings = data.get("results", [])
    findings: list[dict] = []

    for raw_finding in raw_findings:
        finding, normalized_severity = _build_semgrep_finding(raw_finding)
        result["severity_counts"][normalized_severity] += 1
        findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
