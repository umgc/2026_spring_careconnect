"""
TruffleHog Parser (Secrets Detection)

Purpose
-------
Parse TruffleHog JSONL output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/trufflehog.jsonl

Native TruffleHog Severity Model
--------------------------------
TruffleHog does not use a traditional severity scale.
Instead, each finding carries a Verified boolean indicating whether
the secret was confirmed active against its target service.

Verified = true
    Secret is confirmed active and represents a live credential.
Verified = false
    Secret pattern matched but was not confirmed active.

Severity Mapping
----------------
TruffleHog -> Normalized

- Verified = true -> critical
- Verified = false -> high

Behavior
--------
- Reads a JSONL artifact with one JSON object per line.
- Skips blank lines and self-referential findings.
- Maps verified status to normalized severity.
- Populates findings with per-secret detail.
- Never writes raw secret values to findings or metadata.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Counts malformed JSONL lines and records them in metadata.
- Does not apply policy thresholds.

TruffleHog JSONL Record Structure
---------------------------------
{
  "DetectorName": "Github",
  "Verified": false,
  "Raw": "<secret>",
  "SourceMetadata": {
    "Data": {
      "Filesystem": {
        "file": "/repo/path/to/file.py",
        "line": 42
      }
    }
  },
  "ExtraData": {
    "rotation_guide": "https://..."
  }
}

Security Note
-------------
The Raw field contains the actual secret value.
It must never appear in findings, metadata, or any output artifact.
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _read_trufflehog_lines(artifact: Path, result: dict) -> list[str] | None:
    """
    Read the TruffleHog JSONL artifact safely.

    Parameters
    ----------
    artifact : Path
        Path to the JSONL artifact.
    result : dict
        Output result dictionary updated on read failure.

    Returns
    -------
    list[str] | None
        File lines on success, otherwise None.
    """
    try:
        return artifact.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Failed to read trufflehog.jsonl: {error}"
        return None


def _parse_record(raw_line: str) -> dict | None:
    """
    Parse one JSONL line into a dictionary.

    Parameters
    ----------
    raw_line : str
        One stripped JSONL line.

    Returns
    -------
    dict | None
        Parsed record or None if the line is invalid JSON.
    """
    try:
        parsed = json.loads(raw_line)
    except json.JSONDecodeError:
        return None

    return parsed if isinstance(parsed, dict) else None


def _extract_filesystem_data(record: dict) -> dict:
    """
    Extract filesystem metadata from a TruffleHog record.

    Parameters
    ----------
    record : dict
        Parsed TruffleHog record.

    Returns
    -------
    dict
        Filesystem sub-dictionary if present, otherwise empty dict.
    """
    source_metadata = record.get("SourceMetadata") or {}
    source_data = source_metadata.get("Data") or {}
    filesystem_data = source_data.get("Filesystem") or {}
    return filesystem_data if isinstance(filesystem_data, dict) else {}


def _is_self_referential(file_path: str | None) -> bool:
    """
    Detect whether TruffleHog scanned its own output artifact.

    Parameters
    ----------
    file_path : str | None
        Repository-relative file path.

    Returns
    -------
    bool
        True when the finding points to trufflehog.jsonl itself.
    """
    return bool(file_path and file_path.endswith("quality/analysis/raw/trufflehog.jsonl"))


def _build_finding(record: dict) -> tuple[dict, str]:
    """
    Convert one TruffleHog record into a normalized finding.

    Parameters
    ----------
    record : dict
        Parsed TruffleHog record.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    filesystem_data = _extract_filesystem_data(record)
    file_path = _repo_relpath(filesystem_data.get("file"))
    line_number = filesystem_data.get("line")

    detector = record.get("DetectorName") or "TruffleHog"
    verified = bool(record.get("Verified", False))
    normalized_severity = "critical" if verified else "high"

    extra_data = record.get("ExtraData") or {}
    rotation_guide = (
        extra_data.get("rotation_guide")
        if isinstance(extra_data, dict)
        else None
    )

    message = (
        f"{detector} secret detected "
        f"({'VERIFIED' if verified else 'unverified'})"
    )

    finding = {
        "severity": normalized_severity,
        "native_severity": "verified" if verified else "unverified",
        "rule": detector,
        "message": message,
        "file": file_path or "unknown",
        "line": line_number if line_number is not None else 0,
        "rule_url": rotation_guide or "",
    }
    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_trufflehog(raw_dir: Path) -> dict:
    """
    Parse TruffleHog JSONL and return a standardized result dictionary.

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
    - Never writes raw secret values to any output field.
    - Missing artifact sets artifact_present=False and runtime_error=True.
    - Malformed lines are counted in metadata and parsing continues.
    - Empty file is treated as a valid execution with zero violations.
    """
    result = base_tool_result("trufflehog")
    artifact = raw_dir / "trufflehog.jsonl"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        result["metadata"]["error"] = "Missing artifact: trufflehog.jsonl"
        return result

    result["artifact_present"] = True

    lines = _read_trufflehog_lines(artifact, result)
    if lines is None:
        return result

    result["executed"] = True
    findings: list[dict] = []
    malformed_count = 0

    for raw_line in lines:
        stripped_line = raw_line.strip()
        if not stripped_line:
            continue

        record = _parse_record(stripped_line)
        if record is None:
            malformed_count += 1
            continue

        filesystem_data = _extract_filesystem_data(record)
        file_path = _repo_relpath(filesystem_data.get("file"))
        if _is_self_referential(file_path):
            continue

        finding, normalized_severity = _build_finding(record)
        result["severity_counts"][normalized_severity] += 1
        findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    if malformed_count:
        result["metadata"]["malformed_lines"] = malformed_count

    return result


def _repo_relpath(path: str | None) -> str | None:
    """
    Convert an absolute TruffleHog container path to a repository-relative path.

    TruffleHog commonly runs inside Docker with the repository mounted at /repo.
    Paths in the output are therefore often prefixed with /repo/ and should be
    converted to repository-relative paths for downstream reporting.

    Parameters
    ----------
    path : str | None
        Absolute path from TruffleHog output, or None.

    Returns
    -------
    str | None
        Repository-relative path, or None if the input is empty.
    """
    if not path:
        return None

    if path.startswith("/repo/"):
        return path[len("/repo/"):]

    return path
