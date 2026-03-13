"""
HTMLHint Parser (HTML Static Analysis)

Purpose
-------
Parse HTMLHint JSON output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/htmlhint.json

Native HTMLHint Severities
--------------------------
error
    Rule violation that should be fixed.
warning
    Potential issue; advisory.

Severity Mapping
----------------
HTMLHint -> Normalized

- error -> high
- warning -> medium
- unknown -> low

HTMLHint JSON Structure
-----------------------
[
  {
    "filePath": "frontend/web/index.html",
    "messages": [
      {
        "rule": { "id": "doctype-first", "description": "..." },
        "message": "Doctype must be declared first.",
        "line": 1,
        "col": 1,
        "type": "error"
      }
    ]
  }
]
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "error": "high",
    "warning": "medium",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_htmlhint_data(artifact: Path, result: dict) -> list:
    """
    Load the HTMLHint JSON artifact safely.

    Parameters
    ----------
    artifact : Path
        Path to htmlhint.json.
    result : dict
        Result dictionary updated on load failure.

    Returns
    -------
    list
        Parsed list of file results, or an empty list when the JSON
        structure is not a list or loading fails.
    """
    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            data = json.load(file_handle)
    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"HTMLHint parse error: {error}"
        return []

    return data if isinstance(data, list) else []


def _extract_rule_id(rule_value: object) -> str:
    """
    Extract the HTMLHint rule identifier.

    Parameters
    ----------
    rule_value : object
        Rule field from an HTMLHint message.

    Returns
    -------
    str
        Rule identifier as a string.
    """
    if isinstance(rule_value, dict):
        return rule_value.get("id", "unknown")
    return str(rule_value)


def _build_htmlhint_finding(file_path: str, message: dict) -> tuple[dict, str]:
    """
    Convert one HTMLHint message into a normalized finding.

    Parameters
    ----------
    file_path : str
        Source file path for the message.
    message : dict
        Raw HTMLHint message dictionary.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    native_severity = (message.get("type") or "warning").lower()
    normalized_severity = SEVERITY_MAP.get(native_severity, "low")

    finding = {
        "file": file_path,
        "line": message.get("line", 0),
        "column": message.get("col", 0),
        "severity": normalized_severity,
        "native_severity": native_severity,
        "rule": _extract_rule_id(message.get("rule", {})),
        "message": message.get("message", ""),
    }
    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_htmlhint(raw_dir: Path) -> dict:
    """
    Parse htmlhint.json and return a standardized result dictionary.

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
    result = base_tool_result("htmlhint")
    artifact = raw_dir / "htmlhint.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    file_results = _load_htmlhint_data(artifact, result)
    if result["runtime_error"]:
        return result

    findings: list[dict] = []

    for file_result in file_results:
        file_path = file_result.get("file", file_result.get("filePath", "unknown"))
        messages = file_result.get("messages", [])

        for message in messages:
            finding, normalized_severity = _build_htmlhint_finding(file_path, message)
            result["severity_counts"][normalized_severity] += 1
            findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
