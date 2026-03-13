"""
Checkstyle Parser (Java Style Enforcement)

Purpose
-------
Parse the Checkstyle XML report and convert its findings into the
standardized schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/checkstyle.xml

Native Checkstyle Severities
----------------------------
error
    A rule violation treated as an error.
warning
    A rule violation treated as a warning.
info
    Informational finding at the lowest enforcement level.
ignore
    Suppressed rule that does not appear in XML output.

Severity Mapping
----------------
Checkstyle -> Normalized

- error -> high
- warning -> medium
- info -> low

Behavior
--------
- Parses every <error> node across all <file> nodes.
- Maps native severity to normalized severity.
- Populates findings with per-violation detail.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Does not apply policy thresholds.

Checkstyle XML Structure
------------------------
<checkstyle>
  <file name="path/to/File.java">
    <error line="12" column="4" severity="error"
           message="..." source="com.puppycrawl.tools..."/>
  </file>
</checkstyle>
"""

import xml.etree.ElementTree as ET
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "error": "high",
    "warning": "medium",
    "info": "low",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_checkstyle_root(artifact: Path, result: dict) -> ET.Element | None:
    """
    Load and parse the Checkstyle XML artifact.

    Parameters
    ----------
    artifact : Path
        Path to the Checkstyle XML report.
    result : dict
        Result dictionary updated on parse failure.

    Returns
    -------
    ET.Element | None
        Parsed XML root element, or None on failure.
    """
    try:
        tree = ET.parse(artifact)
        return tree.getroot()
    except (ET.ParseError, OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Checkstyle parse error: {error}"
        return None


def _to_int(value: str) -> int:
    """
    Convert a numeric string to int safely.

    Parameters
    ----------
    value : str
        String value from an XML attribute.

    Returns
    -------
    int
        Parsed integer value, or 0 if not numeric.
    """
    return int(value) if value.isdigit() else 0


def _build_checkstyle_finding(
    file_path: str,
    error_node: ET.Element,
) -> tuple[dict, str]:
    """
    Convert one Checkstyle <error> node into a normalized finding.

    Parameters
    ----------
    file_path : str
        Source file path from the parent <file> node.
    error_node : ET.Element
        Checkstyle <error> element.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    native_severity = error_node.attrib.get("severity", "info")
    normalized_severity = SEVERITY_MAP.get(native_severity, "low")

    finding = {
        "file": file_path,
        "line": _to_int(error_node.attrib.get("line", "0")),
        "column": _to_int(error_node.attrib.get("column", "0")),
        "severity": normalized_severity,
        "native_severity": native_severity,
        "message": error_node.attrib.get("message", ""),
        "rule": error_node.attrib.get("source", "unknown"),
    }
    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_checkstyle(raw_dir: Path) -> dict:
    """
    Parse Checkstyle XML and return a standardized result dictionary.

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
    - Malformed XML sets runtime_error=True and records the error in metadata.
    """
    result = base_tool_result("checkstyle")
    artifact = raw_dir / "checkstyle.xml"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    root = _load_checkstyle_root(artifact, result)
    if root is None:
        return result

    findings: list[dict] = []

    for file_node in root.findall("file"):
        file_path = file_node.attrib.get("name", "unknown")

        for error_node in file_node.findall("error"):
            finding, normalized_severity = _build_checkstyle_finding(
                file_path,
                error_node,
            )
            result["severity_counts"][normalized_severity] += 1
            findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
