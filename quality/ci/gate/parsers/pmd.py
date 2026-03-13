"""
PMD Parser (Java Source Static Analysis)

Purpose
-------
Parse PMD XML output and normalize findings into the standard
schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/pmd.xml

Native PMD Severities
---------------------
PMD uses numeric priorities from 1 to 5, where lower numbers are
more severe.

Priority 1
    High priority and most severe.
Priority 2
    Medium-high priority.
Priority 3
    Medium priority.
Priority 4
    Medium-low priority.
Priority 5
    Low priority and least severe.

Severity Mapping
----------------
PMD -> Normalized

- Priority 1 -> critical
- Priority 2 -> high
- Priority 3 -> medium
- Priority 4 -> low
- Priority 5 -> info
- unknown -> info

Behavior
--------
- Parses every <violation> node across all <file> nodes.
- Maps native priority to normalized severity.
- Populates findings with per-violation detail.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Does not apply policy thresholds.

PMD XML Structure
-----------------
<pmd>
  <file name="/path/to/File.java">
    <violation beginline="12" endline="12"
               begincolumn="1" endcolumn="10"
               rule="UnusedVariable" ruleset="Best Practices"
               priority="2" externalInfoUrl="https://...">
      Description of the violation.
    </violation>
  </file>
</pmd>
"""

import xml.etree.ElementTree as ET
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "1": "critical",
    "2": "high",
    "3": "medium",
    "4": "low",
    "5": "info",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_pmd_root(artifact: Path, result: dict) -> ET.Element | None:
    """
    Load and parse the PMD XML artifact.

    Parameters
    ----------
    artifact : Path
        Path to the PMD XML report.
    result : dict
        Result dictionary updated on parse failure.

    Returns
    -------
    ET.Element | None
        Parsed XML root element, or None on failure.
    """
    try:
        tree = ET.parse(artifact)
        root = tree.getroot()
        _strip_pmd_namespaces(root)
        return root
    except (ET.ParseError, OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"PMD parse error: {error}"
        return None


def _strip_pmd_namespaces(root: ET.Element) -> None:
    """
    Remove XML namespaces in-place from the PMD tree.

    Parameters
    ----------
    root : ET.Element
        Root XML element.
    """
    for element in root.iter():
        if "}" in element.tag:
            element.tag = element.tag.split("}", 1)[1]


def _to_int(value: str | int) -> int:
    """
    Convert an XML attribute value to an integer safely.

    Parameters
    ----------
    value : str | int
        Attribute value.

    Returns
    -------
    int
        Parsed integer, or 0 if parsing fails.
    """
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _build_pmd_finding(file_path: str, violation: ET.Element) -> tuple[dict, str]:
    """
    Convert one PMD <violation> node into a normalized finding.

    Parameters
    ----------
    file_path : str
        Source file path from the parent <file> node.
    violation : ET.Element
        PMD <violation> element.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    native_priority = violation.attrib.get("priority", "5")
    normalized_severity = SEVERITY_MAP.get(native_priority, "info")

    finding = {
        "file": file_path,
        "line": _to_int(violation.attrib.get("beginline", 0)),
        "severity": normalized_severity,
        "native_severity": f"priority {native_priority}",
        "rule": violation.attrib.get("rule", "unknown"),
        "ruleset": violation.attrib.get("ruleset", "unknown"),
        "message": (violation.text or "").strip(),
        "rule_url": violation.attrib.get("externalInfoUrl", ""),
    }
    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_pmd(raw_dir: Path) -> dict:
    """
    Parse PMD XML and return a standardized result dictionary.

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
    result = base_tool_result("pmd")
    artifact = raw_dir / "pmd.xml"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    root = _load_pmd_root(artifact, result)
    if root is None:
        return result

    findings: list[dict] = []

    for file_node in root.findall("file"):
        file_path = file_node.attrib.get("name", "unknown")

        for violation in file_node.findall("violation"):
            finding, normalized_severity = _build_pmd_finding(file_path, violation)
            result["severity_counts"][normalized_severity] += 1
            findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
