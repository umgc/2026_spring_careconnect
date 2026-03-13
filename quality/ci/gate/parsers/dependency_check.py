"""
OWASP Dependency-Check Parser (Software Composition Analysis)

Purpose
-------
Parse the OWASP Dependency-Check JSON report and normalize
vulnerabilities into the standard schema defined in schemas.py.

Expected Raw Artifact
---------------------
quality/analysis/raw/dependency-check-report.json

Native Dependency-Check Severities
----------------------------------
CRITICAL
    Typically CVSS v3 score 9.0–10.0
HIGH
    Typically CVSS v3 score 7.0–8.9
MEDIUM
    Typically CVSS v3 score 4.0–6.9
LOW
    Typically CVSS v3 score 0.1–3.9
INFORMATIONAL
    Informational only, no CVSS score or unscored

Severity Mapping
----------------
Dependency-Check -> Normalized

- CRITICAL -> critical
- HIGH -> high
- MEDIUM -> medium
- LOW -> low
- INFORMATIONAL -> info
- unknown -> info

Behavior
--------
- Iterates all dependencies and their vulnerability lists.
- Counts each vulnerability as one violation.
- Populates findings with per-vulnerability detail.
- Prefers CVSS v3 score and falls back to CVSS v2 if v3 is absent.
- Sets max_severity to the highest normalized severity found.
- Does not apply enforcement thresholds.

Dependency-Check JSON Structure
-------------------------------
{
  "dependencies": [
    {
      "fileName": "spring-core-5.3.18.jar",
      "packages": [ { "id": "pkg:maven/..." } ],
      "vulnerabilities": [
        {
          "name": "CVE-2022-22965",
          "severity": "CRITICAL",
          "description": "...",
          "cwes": ["CWE-94"],
          "cvssv3": {
            "baseScore": 9.8,
            "attackVector": "NETWORK"
          },
          "cvssv2": { "score": 7.5 },
          "references": [
            {
              "name": "NVD",
              "url": "https://nvd.nist.gov/vuln/detail/CVE-2022-22965"
            }
          ]
        }
      ]
    }
  ]
}

NVD API Key
-----------
OWASP Dependency-Check uses the NVD API to fetch vulnerability data.
Without a key, requests are rate-limited and scans may be slow or fail.

Once you receive your NVD API key:

Step 1
    Add it as a GitHub Actions secret:
    Name: NVD_API_KEY
    Value: <your key from https://nvd.nist.gov/developers/request-an-api-key>

Step 2
    Pass it to the Dependency-Check CLI in the workflow step:
    --nvdApiKey "${{ secrets.NVD_API_KEY }}"

Step 3
    Remove --noupdate from the workflow step, if present,
    because it prevents the NVD database from being updated.
"""

import json
from pathlib import Path

from quality.ci.gate.schemas import base_tool_result
from quality.ci.gate.utils import determine_max_severity


SEVERITY_MAP = {
    "critical": "critical",
    "high": "high",
    "medium": "medium",
    "low": "low",
    "informational": "info",
}


# ----------------------------------------------------------
# Helper functions
# ----------------------------------------------------------

def _load_dependency_check_data(artifact: Path, result: dict) -> dict | None:
    """
    Load the Dependency-Check JSON artifact safely.

    Parameters
    ----------
    artifact : Path
        Path to dependency-check-report.json.
    result : dict
        Result dictionary updated on load failure.

    Returns
    -------
    dict | None
        Parsed JSON document, or None on failure.
    """
    try:
        with open(artifact, "r", encoding="utf-8") as file_handle:
            return json.load(file_handle)
    except (OSError, TypeError, ValueError, KeyError) as error:
        result["runtime_error"] = True
        result["metadata"]["error"] = f"Dependency-Check parse error: {error}"
        return None


def _get_package_id(dependency: dict) -> str:
    """
    Extract the first package ID from a dependency record.

    Parameters
    ----------
    dependency : dict
        One dependency object from the report.

    Returns
    -------
    str
        Package ID when available, otherwise 'unknown'.
    """
    packages = dependency.get("packages", [])
    if packages and isinstance(packages[0], dict):
        return packages[0].get("id", "unknown")
    return "unknown"


def _normalize_cwes(cwes: list | str | None) -> list:
    """
    Normalize CWE values to a list.

    Parameters
    ----------
    cwes : list | str | None
        CWE field from a vulnerability record.

    Returns
    -------
    list
        Normalized list of CWE values.
    """
    if cwes is None:
        return []
    if isinstance(cwes, str):
        return [cwes]
    return cwes


def _get_nvd_url(references: list) -> str:
    """
    Extract the NVD reference URL if present.

    Parameters
    ----------
    references : list
        Reference list from a vulnerability record.

    Returns
    -------
    str
        NVD URL when present, otherwise empty string.
    """
    for reference in references:
        if isinstance(reference, dict) and reference.get("name", "").upper() == "NVD":
            return reference.get("url", "")
    return ""


def _truncate_description(description: str, max_length: int = 500) -> str:
    """
    Trim long vulnerability descriptions for report readability.

    Parameters
    ----------
    description : str
        Vulnerability description text.
    max_length : int, optional
        Maximum output length.

    Returns
    -------
    str
        Description text trimmed when necessary.
    """
    if len(description) > max_length:
        return description[:max_length] + "..."
    return description


def _build_dependency_check_finding(
    file_name: str,
    package_id: str,
    vulnerability: dict,
) -> tuple[dict, str]:
    """
    Convert one vulnerability into a normalized finding.

    Parameters
    ----------
    file_name : str
        Dependency file name.
    package_id : str
        Package identifier for the dependency.
    vulnerability : dict
        Vulnerability record from Dependency-Check.

    Returns
    -------
    tuple[dict, str]
        Normalized finding and its normalized severity.
    """
    cve = vulnerability.get("name", "unknown")
    native_severity = vulnerability.get("severity", "INFORMATIONAL")
    normalized_severity = SEVERITY_MAP.get(native_severity.lower(), "info")

    cvss_v3 = vulnerability.get("cvssv3", {})
    cvss_v2 = vulnerability.get("cvssv2", {})
    references = vulnerability.get("references", [])

    finding = {
        "file": file_name,
        "package": package_id,
        "cve": cve,
        "rule": cve,
        "severity": normalized_severity,
        "native_severity": native_severity,
        "cvss_score": cvss_v3.get("baseScore") or cvss_v2.get("score"),
        "cvss_vector": cvss_v3.get("attackVector", ""),
        "cwe": _normalize_cwes(vulnerability.get("cwes", [])),
        "nvd_url": _get_nvd_url(references),
        "references": references,
        "description": _truncate_description(vulnerability.get("description", "")),
    }
    return finding, normalized_severity


def _collect_dependency_findings(dependencies: list, result: dict) -> list[dict]:
    """
    Collect normalized findings from all dependencies.

    Parameters
    ----------
    dependencies : list
        Dependency list from the report.
    result : dict
        Result dictionary updated with severity counts.

    Returns
    -------
    list[dict]
        All normalized findings from the report.
    """
    findings: list[dict] = []

    for dependency in dependencies:
        file_name = dependency.get("fileName", "unknown")
        package_id = _get_package_id(dependency)
        vulnerabilities = dependency.get("vulnerabilities", [])

        for vulnerability in vulnerabilities:
            finding, normalized_severity = _build_dependency_check_finding(
                file_name,
                package_id,
                vulnerability,
            )
            result["severity_counts"][normalized_severity] += 1
            findings.append(finding)

    return findings


# ----------------------------------------------------------
# Main parser
# ----------------------------------------------------------

def parse_dependency_check(raw_dir: Path) -> dict:
    """
    Parse Dependency-Check JSON and return a standardized result dictionary.

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
    """
    result = base_tool_result("dependency_check")
    artifact = raw_dir / "dependency-check-report.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"] = True
        return result

    result["artifact_present"] = True
    result["executed"] = True

    data = _load_dependency_check_data(artifact, result)
    if data is None:
        return result

    dependencies = data.get("dependencies", [])
    findings = _collect_dependency_findings(dependencies, result)

    result["findings"] = findings
    result["violation_count"] = len(findings)
    result["max_severity"] = determine_max_severity(result["severity_counts"])

    return result
