"""
SpotBugs Parser (Java Bytecode Static Analysis)

Purpose
-------
Parse SpotBugs XML output and normalize findings into the
standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/spotbugs.xml

Native SpotBugs Severities
--------------------------
SpotBugs reports a numeric priority on each BugInstance.

Priority 1
    High and most severe.
Priority 2
    Medium.
Priority 3
    Low and least severe.

Severity Mapping
----------------
SpotBugs -> Normalized

- Priority 1 -> high
- Priority 2 -> medium
- Priority 3 -> low
- unknown -> info

Behavior
--------
- Parses every <BugInstance> node in the XML report.
- Maps native priority to normalized severity.
- Populates findings with per-bug detail.
- Counts violations per normalized severity level.
- Sets max_severity to the highest normalized severity found.
- Does not apply policy thresholds.

SpotBugs XML Structure
----------------------
<BugCollection>
  <BugInstance type="NP_NULL_ON_SOME_PATH" priority="1" rank="4"
               abbrev="NP" category="CORRECTNESS">
    <Class classname="com.careconnect.auth.LoginService" />
    <Method name="authenticate" signature="..." isStatic="false" />
    <SourceLine classname="com.careconnect.auth.LoginService"
                start="42" end="42"
                sourcefile="LoginService.java"
                sourcepath="com/careconnect/auth/LoginService.java" />
    <ShortMessage>Null pointer dereference...</ShortMessage>
  </BugInstance>
</BugCollection>
"""

import xml.etree.ElementTree as ET
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "1": "high",
    "2": "medium",
    "3": "low",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_spotbugs_root(artifact: Path, result: dict) -> ET.Element | None:
    """
    Load and parse the SpotBugs XML artifact.

    Parameters
    ----------
    artifact : Path
        Path to the SpotBugs XML report.
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
        result["metadata"]["error"] = f"SpotBugs parse error: {error}"
        return None


def _extract_source_info(bug: ET.Element) -> tuple[str, int]:
    """
    Extract source file and starting line from a BugInstance.

    Parameters
    ----------
    bug : ET.Element
        SpotBugs BugInstance element.

    Returns
    -------
    tuple[str, int]
        Source file path and starting line number.
    """
    source_line = bug.find("SourceLine")
    if source_line is None:
        return "unknown", 0

    source_file = source_line.attrib.get("sourcepath", "unknown")
    line_start = int(source_line.attrib.get("start", 0))
    return source_file, line_start


def _extract_message(bug: ET.Element) -> str:
    """
    Extract the short human-readable message from a BugInstance.

    Parameters
    ----------
    bug : ET.Element
        SpotBugs BugInstance element.

    Returns
    -------
    str
        Short message text, or empty string if unavailable.
    """
    short_message = bug.find("ShortMessage")
    if short_message is not None and short_message.text:
        return short_message.text.strip()
    return ""


def _extract_class_name(bug: ET.Element) -> str:
    """
    Extract the Java class name from a BugInstance.

    Parameters
    ----------
    bug : ET.Element
        SpotBugs BugInstance element.

    Returns
    -------
    str
        Java class name, or 'unknown' if unavailable.
    """
    class_node = bug.find("Class")
    if class_node is not None:
        return class_node.attrib.get("classname", "unknown")
    return "unknown"


def _build_spotbugs_finding(bug: ET.Element) -> tuple[dict, str]:
    """
    Convert one SpotBugs BugInstance into a normalized finding.

    Parameters
    ----------
    bug : ET.Element
        SpotBugs BugInstance element.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    native_priority = bug.attrib.get("priority", "")
    normalized_severity = SEVERITY_MAP.get(native_priority, "info")

    source_file, line_start = _extract_source_info(bug)
    message = _extract_message(bug)
    class_name = _extract_class_name(bug)

    finding = {
        "file": source_file,
        "line": line_start,
        "severity": normalized_severity,
        "native_severity": (
            f"priority {native_priority}" if native_priority else "unknown"
        ),
        "rule": bug.attrib.get("type", "unknown"),
        "category": bug.attrib.get("category", "unknown"),
        "class": class_name,
        "message": message,
    }
    return finding, normalized_severity


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_spotbugs(raw_dir: Path) -> dict:
    """
    Parse spotbugs.xml and return a standardized result dictionary.

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
    result = base_tool_result("spotbugs")
    artifact = raw_dir / "spotbugs.xml"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    root = _load_spotbugs_root(artifact, result)
    if root is None:
        return result

    findings: list[dict] = []

    for bug in root.iter("BugInstance"):
        finding, normalized_severity = _build_spotbugs_finding(bug)
        result["severity_counts"][normalized_severity] += 1
        findings.append(finding)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
