"""
Trivy Parser (Container + Filesystem SCA)

Purpose:
    Parse Trivy JSON output and normalize findings into the
    standard schema defined in schemas.py.

Expected raw artifact:
    quality/analysis/raw/trivy.json

Native Trivy Severities:
    CRITICAL  → Critical vulnerability, immediate action required.
    HIGH      → High severity vulnerability.
    MEDIUM    → Medium severity vulnerability.
    LOW       → Low severity, informational.
    UNKNOWN   → Severity could not be determined.

Severity Mapping (Trivy → Normalized):
    CRITICAL → critical
    HIGH     → high
    MEDIUM   → medium
    LOW      → low
    UNKNOWN  → info

Trivy JSON Structure (filesystem scan):
    {
      "Results": [
        {
          "Target": "backend/core/pom.xml",
          "Type":   "pom",
          "Vulnerabilities": [
            {
              "VulnerabilityID": "CVE-2021-12345",
              "PkgName":         "log4j",
              "InstalledVersion":"2.14.0",
              "FixedVersion":    "2.17.1",
              "Severity":        "CRITICAL",
              "Title":           "Remote Code Execution",
              "Description":     "..."
            }
          ]
        }
      ]
    }
"""

import json
from pathlib import Path

from ..schemas import base_tool_result
from ..utils import determine_max_severity

SEVERITY_MAP: dict[str, str] = {
    "critical": "critical",
    "high":     "high",
    "medium":   "medium",
    "low":      "low",
    "unknown":  "info",
}


def parse_trivy(raw_dir: Path) -> dict:
    """
    Parse trivy.json and return a standardized result dictionary.

    Args:
        raw_dir: Path to the directory containing raw tool outputs.

    Returns:
        A dict conforming to the base_tool_result schema.

    Contract:
        - Always returns a base_tool_result structure.
        - Never raises exceptions outward.
        - Missing artifact → artifact_present=False, runtime_error=True.
        - Malformed JSON   → runtime_error=True, error in metadata.
        - Empty Results [] → valid result with zero violations.
    """
    result   = base_tool_result("trivy")
    artifact = raw_dir / "trivy.json"

    if not artifact.exists():
        result["artifact_present"] = False
        result["runtime_error"]    = True
        return result

    result["artifact_present"] = True
    result["executed"]         = True

    try:
        with open(artifact, encoding="utf-8") as f:
            data = json.load(f)

        findings  = []
        scan_results = data.get("Results", [])

        for scan_result in scan_results:
            target          = scan_result.get("Target", "unknown")
            vulnerabilities = scan_result.get("Vulnerabilities") or []

            for vuln in vulnerabilities:
                native_sev = (vuln.get("Severity") or "UNKNOWN").lower()
                norm_sev   = SEVERITY_MAP.get(native_sev, "info")
                result["severity_counts"][norm_sev] += 1

                findings.append({
                    "file":             target,
                    "line":             0,
                    "column":           0,
                    "severity":         norm_sev,
                    "native_severity":  vuln.get("Severity", "UNKNOWN"),
                    "rule":             vuln.get("VulnerabilityID", "unknown"),
                    "message": (
                        f"{vuln.get('PkgName', 'unknown')} "
                        f"{vuln.get('InstalledVersion', '')} — "
                        f"{vuln.get('Title', vuln.get('Description', ''))}"
                    ).strip(),
                    "metadata": {
                        "package":           vuln.get("PkgName", ""),
                        "installed_version": vuln.get("InstalledVersion", ""),
                        "fixed_version":     vuln.get("FixedVersion", ""),
                        "cve":               vuln.get("VulnerabilityID", ""),
                    },
                })

        result["findings"]        = findings
        result["violation_count"] = len(findings)
        result["max_severity"]    = determine_max_severity(result["severity_counts"])

    except json.JSONDecodeError as e:
        result["runtime_error"]     = True
        result["metadata"]["error"] = f"JSON parse error: {e}"
    except Exception as e:
        result["runtime_error"]     = True
        result["metadata"]["error"] = f"Unexpected error: {e}"

    return result
