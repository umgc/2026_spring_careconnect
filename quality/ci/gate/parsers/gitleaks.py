"""
Gitleaks Parser (Secrets Detection)

Purpose
-------
Parse Gitleaks JSON output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/gitleaks.json

Security Note
-------------
The Secret and Match fields contain the actual secret value.
They must never appear in findings, metadata, or any output artifact.
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


# ----------------------------------------------------------
# Severity configuration
# ----------------------------------------------------------

_SEVERITY_TAGS = ["critical", "high", "medium", "low"]
_DEFAULT_SEVERITY = "high"


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _normalize_records(raw_text: str) -> list | None:
    """
    Parse raw JSON text and normalize null to an empty list.

    Gitleaks occasionally emits a literal null instead of an empty array.
    """
    records = json.loads(raw_text)
    if records is None:
        return []
    return records


def _read_gitleaks_artifact(artifact: Path, result: dict) -> str | None:
    """
    Read the raw Gitleaks artifact safely.

    If the file cannot be read, the parser records the runtime error
    in the result metadata and returns None.
    """
    try:
        return artifact.read_text(encoding="utf-8", errors="replace").strip()
    except OSError as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Failed to read gitleaks.json: {error}"
        return None


def _parse_gitleaks_records(raw_text: str, result: dict) -> list | None:
    """
    Convert raw JSON text into a list of records.

    This function also validates that the structure is the expected array.
    """
    try:
        records = _normalize_records(raw_text)
    except (TypeError, ValueError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Failed to parse gitleaks.json: {error}"
        return None

    if not isinstance(records, list):
        result["runtime_error"] = True
        result["metadata"]["error"] = (
            "Unexpected gitleaks.json structure: expected array, "
            f"got {type(records).__name__}"
        )
        return None

    return records


def _is_self_referential_gitleaks_record(record: dict) -> bool:
    """
    Detect Gitleaks scanning its own output artifact.

    The tool sometimes scans the report file itself which must
    be ignored to avoid self-reporting false positives.
    """
    file_path = record.get("File") or record.get("SymlinkFile") or ""
    return bool(
        file_path and file_path.endswith("quality/analysis/raw/gitleaks.json")
    )


def _determine_severity(tags: list[str]) -> str:
    """
    Determine normalized severity based on rule tags.

    If no severity tag exists, default severity is HIGH.
    """
    for level in _SEVERITY_TAGS:
        if level in tags:
            return level
    return _DEFAULT_SEVERITY


def _build_message(record: dict, default_desc: str) -> str:
    """
    Build a sanitized message for the finding.

    The message may include entropy when available but must
    never expose the actual secret value.
    """
    entropy = record.get("Entropy")

    if entropy is None:
        return default_desc

    try:
        return f"{default_desc} (entropy: {float(entropy):.2f})"
    except (TypeError, ValueError):
        return default_desc


def _build_gitleaks_finding(record: dict) -> tuple[dict, str]:
    """
    Convert a single Gitleaks record into a normalized finding.

    Returns both the normalized finding and the normalized severity.
    """
    file_path = record.get("File") or record.get("SymlinkFile") or "unknown"
    tags = [str(tag).lower() for tag in (record.get("Tags") or [])]

    normalized_severity = _determine_severity(tags)

    rule_id = record.get("RuleID") or "gitleaks"
    description = record.get("Description") or rule_id

    finding = {
        "severity": normalized_severity,
        "native_severity": ",".join(tags) if tags else "none",
        "rule": rule_id,
        "message": _build_message(record, description),
        "file": file_path,
        "line": record.get("StartLine") or 0,
        "rule_url": "",
    }

    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_gitleaks(raw_dir: Path) -> dict:
    """
    Parse Gitleaks JSON and return a standardized result dictionary.

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
    - Never writes raw secret values.
    - Missing artifact sets artifact_present=False and runtime_error=True.
    - Empty array is treated as a valid execution with zero violations.
    """
    tool_name = "gitleaks"
    result = base_tool_result(tool_name)

    artifact = raw_dir / "gitleaks.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        result["metadata"]["error"] = "Missing artifact: gitleaks.json"
        return result

    result["artifact_present"] = True

    raw_text = _read_gitleaks_artifact(artifact, result)
    if raw_text is None:
        return result

    if not raw_text:
        result["executed"] = True
        result["findings"] = []
        result["violation_count"] = 0
        result["max_severity"] = None
        return result

    records = _parse_gitleaks_records(raw_text, result)
    if records is None:
        return result

    result["executed"] = True

    findings = []

    for record in records:

        if not isinstance(record, dict):
            continue

        if _is_self_referential_gitleaks_record(record):
            continue

        finding, normalized_severity = _build_gitleaks_finding(record)

        result["severity_counts"][normalized_severity] += 1
        findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
