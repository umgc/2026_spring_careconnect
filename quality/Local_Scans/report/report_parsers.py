"""
Report Parsers

Parses raw artifacts produced by each tool and returns
a normalized list of findings and severity counts.

Functions:
  parse_flutter(path)               → findings, sev_counts
  parse_checkstyle(path, repo_root) → findings, sev_counts
  parse_pmd(path, repo_root)        → findings, sev_counts
  parse_spotbugs(path)              → findings, sev_counts

Each finding dict contains:
  severity  — critical | high | medium | low | info
  file      — relative file path
  line      — line number as string
  rule      — rule or bug type name
  message   — human-readable description
"""

import re
import xml.etree.ElementTree as ET
from pathlib import Path


def _strip_root(path: str, repo_root: str) -> str:
    """Strip repo root prefix from an absolute path."""
    if repo_root in path:
        return path[len(repo_root) :].lstrip("/\\")
    return path


def _empty_sev() -> dict:
    """Return an empty severity-count map."""
    return {"critical": 0, "high": 0, "medium": 0, "low": 0, "info": 0}


def _strip_namespace(root: ET.Element) -> str:
    """Extract namespace prefix from root tag if present."""
    if root.tag.startswith("{"):
        return root.tag.split("}")[0] + "}"
    return ""


# ----------------------------------------------------------
# Flutter Analyze
# ----------------------------------------------------------
# Flutter analyzer output varies by platform/terminal. Common formats:
#   warning • message • lib/path/file.dart:10:5 • rule_code
#   warning - message at lib/path/file.dart:10:5 - rule_code
#   warning - message - lib/path/file.dart:10:5 - rule_code
FLUTTER_ISSUE_PATTERNS = [
    re.compile(
        r"^\s*(error|warning|info|hint)\s*•\s*"
        r"(.+?)\s*•\s*"
        r"([^\s].*?):(\d+):\d+\s*•\s*"
        r"(\S+)\s*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^\s*(error|warning|info|hint)\s*-\s*"
        r"(.+?)\s+at\s+"
        r"([^\s].*?):(\d+):\d+\s*-\s*"
        r"(\S+)\s*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^\s*(error|warning|info|hint)\s*-\s*"
        r"(.+?)\s*-\s*"
        r"([^\s].*?):(\d+):\d+\s*-\s*"
        r"(\S+)\s*$",
        re.IGNORECASE,
    ),
]


def _parse_flutter_line(line: str) -> tuple[str, str, str, str, str] | None:
    """Parse one flutter analyze finding line across known output formats."""
    for pattern in FLUTTER_ISSUE_PATTERNS:
        match = pattern.match(line)
        if match:
            return match.groups()
    return None


def parse_flutter(path: Path) -> tuple[list, dict]:
    """
    Parse flutter_analyze.txt plain text output.

    Native severity mapping:
      error   → high
      warning → medium
      info    → low
      hint    → low

    Note: only errors block the commit. Warnings and hints
    are reported but do not fail the gate.
    """
    findings: list = []
    sev_counts: dict = _empty_sev()

    sev_map = {
        "error": "high",
        "warning": "medium",
        "info": "low",
        "hint": "low",
    }

    try:
        text = path.read_text(encoding="utf-8", errors="replace")
        for line in text.splitlines():
            parsed = _parse_flutter_line(line)
            if not parsed:
                continue

            native, message, file_path, line_no, rule = parsed
            sev = sev_map.get(native.lower(), "low")
            sev_counts[sev] += 1
            findings.append(
                {
                    "severity": sev,
                    "file": file_path.strip(),
                    "line": line_no,
                    "rule": rule.strip(),
                    "message": message.strip(),
                }
            )
    except (OSError, ValueError) as error:
        print(
            f"[report-parsers] Warning: could not parse flutter_analyze.txt: {error}"
        )

    return findings, sev_counts


# ----------------------------------------------------------
# Checkstyle
# ----------------------------------------------------------
def parse_checkstyle(path: Path, repo_root: str) -> tuple[list, dict]:
    """
    Parse checkstyle.xml.

    Native severity mapping:
      error   → high
      warning → medium
      info    → low
    """
    findings: list = []
    sev_counts: dict = _empty_sev()

    try:
        tree = ET.parse(str(path))
        root = tree.getroot()
        ns = _strip_namespace(root)

        for file_el in root.findall(f"{ns}file"):
            fname = _strip_root(file_el.get("name", "unknown"), repo_root)
            for err in file_el.findall(f"{ns}error"):
                native = (err.get("severity") or "info").lower()
                sev = {"error": "high", "warning": "medium", "info": "low"}.get(
                    native, "low"
                )
                sev_counts[sev] += 1
                findings.append(
                    {
                        "severity": sev,
                        "file": fname,
                        "line": err.get("line", "0"),
                        "rule": (err.get("source") or "").split(".")[-1],
                        "message": err.get("message", ""),
                    }
                )
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"[report-parsers] Warning: could not parse checkstyle.xml: {error}")

    return findings, sev_counts


# ----------------------------------------------------------
# PMD
# ----------------------------------------------------------
def parse_pmd(path: Path, repo_root: str) -> tuple[list, dict]:
    """
    Parse pmd.xml.

    Native priority mapping:
      1 → critical
      2 → high
      3 → medium
      4 → low
      5 → info

    Note: PMD XML includes a namespace which must be stripped
    before findall() will locate child elements correctly.
    """
    findings: list = []
    sev_counts: dict = _empty_sev()

    priority_map = {1: "critical", 2: "high", 3: "medium", 4: "low", 5: "info"}

    try:
        tree = ET.parse(str(path))
        root = tree.getroot()
        ns = _strip_namespace(root)

        for file_el in root.findall(f"{ns}file"):
            fname = _strip_root(file_el.get("name", "unknown"), repo_root)
            for violation in file_el.findall(f"{ns}violation"):
                priority = int(violation.get("priority", "3"))
                sev = priority_map.get(priority, "medium")
                sev_counts[sev] += 1
                findings.append(
                    {
                        "severity": sev,
                        "file": fname,
                        "line": violation.get("beginline", "0"),
                        "rule": violation.get("rule", "unknown"),
                        "message": (violation.text or "").strip(),
                    }
                )
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"[report-parsers] Warning: could not parse pmd.xml: {error}")

    return findings, sev_counts


# ----------------------------------------------------------
# SpotBugs
# ----------------------------------------------------------
def parse_spotbugs(path: Path) -> tuple[list, dict]:
    """
    Parse spotbugs.xml.

    Native priority mapping:
      1 → high
      2 → medium
      3 → low
    """
    findings: list = []
    sev_counts: dict = _empty_sev()

    priority_map = {1: "high", 2: "medium", 3: "low"}

    try:
        tree = ET.parse(str(path))
        root = tree.getroot()
        ns = _strip_namespace(root)

        for bug in root.findall(f"{ns}BugInstance"):
            priority = int(bug.get("priority", "2"))
            sev = priority_map.get(priority, "medium")
            sev_counts[sev] += 1
            src = bug.find(f"{ns}SourceLine")
            file_path = (
                src.get("sourcepath", "unknown") if src is not None else "unknown"
            )
            line = src.get("start", "0") if src is not None else "0"
            findings.append(
                {
                    "severity": sev,
                    "file": file_path,
                    "line": line,
                    "rule": bug.get("type", "unknown"),
                    "message": (bug.findtext(f"{ns}ShortMessage") or "").strip(),
                }
            )
    except (OSError, ET.ParseError, ValueError) as error:
        print(f"[report-parsers] Warning: could not parse spotbugs.xml: {error}")

    return findings, sev_counts
