"""
Normalization Layer (Layer 1 of the Quality Gate Engine)

Purpose
-------
Convert heterogeneous tool outputs (XML, JSON, JSONL) into a single
consistent schema so policy evaluation can remain simple and deterministic.

Inputs
------
Raw tool artifacts read from:
    quality/analysis/raw/

Output
------
A single normalized file written to:
    quality/analysis/normalized/normalized.json

Top-Level normalized.json Structure
-----------------------------------
{
  "generated_at": "2026-02-24T12:00:00Z",
  "tool_count": 8,
  "total_violations": 42,
  "max_severity": "critical",
  "results": [
    {
      "tool": "trufflehog",
      "artifact_present": true,
      "executed": true,
      "runtime_error": false,
      "findings": [ ... ],
      "violation_count": 3,
      "severity_counts": { "critical": 1, "high": 2, ... },
      "max_severity": "critical",
      "metadata": { ... }
    }
  ]
}

Design Rules
------------
- Does not apply policy thresholds. policy_engine.py owns that.
- Does not decide pass/fail. gate.py owns that.
- Must be resilient. One parser crash must not prevent other tools
  from being collected and normalized.
- A parser crash is recorded as runtime_error=True for that tool,
  which the policy engine can treat as a governance violation.

Execution
---------
Recommended:
    python -m quality.ci.gate.normalize

Direct:
    python quality/ci/gate/normalize.py
"""

import json
from datetime import datetime, timezone
from pathlib import Path

from quality.ci.gate.parsers.bandit           import parse_bandit
from quality.ci.gate.parsers.checkstyle       import parse_checkstyle
from quality.ci.gate.parsers.dependency_check import parse_dependency_check
from quality.ci.gate.parsers.flutter          import parse_flutter
from quality.ci.gate.parsers.gitleaks         import parse_gitleaks
from quality.ci.gate.parsers.htmlhint         import parse_htmlhint
from quality.ci.gate.parsers.pmd              import parse_pmd
from quality.ci.gate.parsers.pylint           import parse_pylint
from quality.ci.gate.parsers.semgrep          import parse_semgrep
from quality.ci.gate.parsers.spotbugs         import parse_spotbugs
from quality.ci.gate.parsers.stylelint        import parse_stylelint
from quality.ci.gate.parsers.trufflehog       import parse_trufflehog
from quality.ci.gate.parsers.trivy            import parse_trivy
from quality.ci.gate.schemas                  import base_tool_result
from quality.ci.gate.utils                    import SEVERITY_ORDER, determine_max_severity


RAW_DIR        = Path("quality/analysis/raw")
NORMALIZED_DIR = Path("quality/analysis/normalized")
OUTPUT_FILE    = NORMALIZED_DIR / "normalized.json"


TOOL_PARSERS: list[tuple[str, callable]] = [
    ("trufflehog",       parse_trufflehog),       # Secrets scan            (JSONL)
    ("gitleaks",         parse_gitleaks),          # Secrets scan            (JSON)
    ("flutter_analyze",  parse_flutter),           # Dart static analysis    (JSON)
    ("checkstyle",       parse_checkstyle),        # Java style              (XML)
    ("pmd",              parse_pmd),               # Java source analysis    (XML)
    ("spotbugs",         parse_spotbugs),          # Java bytecode analysis  (XML)
    ("semgrep",          parse_semgrep),           # Multi-language SAST     (JSON)
    ("pylint",           parse_pylint),            # Python static analysis  (JSON)
    ("bandit",           parse_bandit),            # Python security SAST    (JSON)
    ("htmlhint",         parse_htmlhint),          # HTML static analysis    (JSON)
    ("stylelint",        parse_stylelint),         # CSS/SCSS analysis       (JSON)
    ("dependency_check", parse_dependency_check),  # SCA — Multi             (JSON)
    ("trivy",            parse_trivy),             # SCA — Container/FS      (JSON)
]


def normalize() -> list[dict]:
    """
    Run all registered tool parsers and write normalized.json.

    Each parser reads its raw artifact from RAW_DIR and returns a
    standardized result dictionary conforming to schemas.base_tool_result.

    A top-level summary wrapper is written around all tool results so the
    policy engine and reporting layer have a single entry point for overall
    pipeline state.

    Returns
    -------
    list[dict]
        The list of per-tool normalized result dictionaries.

    Contract
    --------
    - Always attempts to run every registered parser.
    - Never aborts due to a single tool failure.
    - Converts parser exceptions into runtime_error records.
    - Creates the output directory if it does not already exist.
    """
    NORMALIZED_DIR.mkdir(parents=True, exist_ok=True)

    results: list[dict] = []

    for tool_name, parser in TOOL_PARSERS:
        try:
            result = parser(RAW_DIR)
            results.append(result)
        except (OSError, ValueError, TypeError, KeyError, RuntimeError) as error:
            error_result = base_tool_result(tool_name)
            error_result["executed"]            = False
            error_result["runtime_error"]       = True
            error_result["metadata"]["error"]   = (
                f"Parser raised an unhandled exception: {error}"
            )
            results.append(error_result)

    total_violations = sum(result.get("violation_count", 0) for result in results)

    combined_severity_counts: dict[str, int] = dict.fromkeys(SEVERITY_ORDER, 0)
    for result in results:
        for level, count in result.get("severity_counts", {}).items():
            if level in combined_severity_counts:
                combined_severity_counts[level] += count

    overall_max_severity = determine_max_severity(combined_severity_counts)
    generated_at         = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    normalized_doc = {
        "generated_at":    generated_at,
        "tool_count":      len(results),
        "total_violations": total_violations,
        "max_severity":    overall_max_severity,
        "results":         results,
    }

    with open(OUTPUT_FILE, "w", encoding="utf-8") as file_handle:
        json.dump(normalized_doc, file_handle, indent=2)

    print(f"[normalize] {len(results)} tool(s) processed.")
    print(f"[normalize] Total violations : {total_violations}")
    print(f"[normalize] Max severity     : {overall_max_severity or 'none'}")
    print(f"[normalize] Output written to: {OUTPUT_FILE}")

    return results


if __name__ == "__main__":
    normalize()
