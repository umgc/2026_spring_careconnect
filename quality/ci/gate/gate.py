"""
Quality Gate Orchestrator (Single Enforcement Authority)

This module is the only file that controls the final CI exit code.

Layer Architecture
------------------
Layer 1 — normalize.py
    Reads raw tool artifacts from quality/analysis/raw/.
    Produces quality/analysis/normalized/normalized.json.

Layer 2 — policy_engine.py
    Reads normalized.json + policy.yaml.
    Produces quality/analysis/evaluated/evaluated.json.
    Determines overall_block (True/False).

Layer 3 — humanize.py
    Reads normalized.json + evaluated.json.
    Produces quality/analysis/human/index.md and per-tool pages.

Layer 4 — report.py
    Called separately by the workflow.
    Reads evaluated.json.
    Produces quality/analysis/report.md.
    Posts or updates the PR comment via the GitHub API.

Responsibilities
----------------
1. Invoke Layers 1–3 in sequence.
2. Write fail-safe evaluated.json if any layer crashes.
3. Honor gate.mode (enforce vs report_only).
4. Exit with the correct status code.

Exit Codes
----------
0
    Approved. Merge allowed.

1
    Blocked. Merge blocked.

Gate Modes
----------
enforce
    Violations block the merge. This is the default and fail-safe mode.

report_only
    Violations are reported but do not block the merge.

Design Rules
------------
- Tool logic belongs in parsers/.
- Policy thresholds belong in policy.yaml.
- Report rendering belongs in report.py.
- This module coordinates only. It contains no tool or policy logic.
- Fail-safe: any unhandled error results in exit code 1.

Execution
---------
python -m quality.ci.gate.gate
"""

import json
from pathlib import Path

import yaml

from quality.ci.gate.humanize import generate_human_readable_outputs
from quality.ci.gate.normalize import normalize
from quality.ci.gate.policy_engine import evaluate


ANALYSIS_DIR = Path("quality/analysis")
POLICY_FILE = Path("quality/ci/gate/policy.yaml")
EVALUATED_DIR = ANALYSIS_DIR / "evaluated"
EVALUATED_FILE = EVALUATED_DIR / "evaluated.json"
NORMALIZED_FILE = ANALYSIS_DIR / "normalized" / "normalized.json"


def _load_gate_mode() -> str:
    """
    Read gate.mode from policy.yaml.

    Returns
    -------
    str
        "enforce" if violations should block the merge.
        "report_only" if violations should be reported without blocking.

    Notes
    -----
    Any error while reading policy.yaml defaults to "enforce"
    as a fail-safe behavior.
    """
    try:
        with open(POLICY_FILE, "r", encoding="utf-8") as file_handle:
            data = yaml.safe_load(file_handle) or {}

        mode = str((data.get("gate", {}) or {}).get("mode", "enforce")).strip().lower()
        return mode if mode in {"enforce", "report_only"} else "enforce"

    except (OSError, TypeError, ValueError, yaml.YAMLError):
        return "enforce"


def _write_failsafe_evaluated(stage: str, error: Exception) -> None:
    """
    Write a minimal evaluated.json document when a pipeline layer fails.

    This guarantees that evaluated.json always exists for downstream
    consumers, even if normalization or policy evaluation crashes.

    Parameters
    ----------
    stage : str
        Name of the failing stage, such as "normalization" or
        "policy_evaluation".
    error : Exception
        Exception raised by the failing stage.
    """
    EVALUATED_DIR.mkdir(parents=True, exist_ok=True)

    gate_engine_entry = {
        "tool": "gate_engine",
        "blocking": True,
        "policy_violation": True,
        "reason": f"{stage}_failed",
        "normalized": {
            "tool": "gate_engine",
            "artifact_present": False,
            "executed": False,
            "runtime_error": True,
            "findings": [],
            "violation_count": 1,
            "severity_counts": {
                "critical": 0,
                "high": 1,
                "medium": 0,
                "low": 0,
                "info": 0,
            },
            "max_severity": "high",
            "metadata": {
                "stage": stage,
                "error": str(error),
            },
        },
    }

    failsafe_doc = {
        "overall_block": True,
        "generated_at": "",
        "blocking_results": [gate_engine_entry],
        "non_blocking_results": [],
    }

    EVALUATED_FILE.write_text(json.dumps(failsafe_doc, indent=2), encoding="utf-8")
    print(f"[gate] Fail-safe evaluated.json written to: {EVALUATED_FILE}")


def main() -> None:
    """
    Execute the quality gate pipeline.

    This function runs Layers 1–3 in sequence, handles failures at each
    stage, and exits with the correct status code based on gate.mode.

    Guarantees
    ----------
    - Always attempts Layers 1–3 in order.
    - Always produces evaluated.json, even on failure.
    - Always honors gate.mode for the final exit code.
    - Never allows an unhandled exception to escape.
    """
    mode = _load_gate_mode()
    blocked = True

    print("[gate] Layer 1: Running normalization...")
    try:
        normalize()
    except (OSError, ValueError, TypeError, KeyError, RuntimeError) as error:
        print(f"[gate] Normalization failed: {error}")
        _write_failsafe_evaluated("normalization", error)
        _exit(blocked=True, mode=mode)

    print("[gate] Layer 2: Applying policy rules...")
    try:
        blocked = evaluate()
    except (OSError, ValueError, TypeError, KeyError, RuntimeError) as error:
        print(f"[gate] Policy evaluation failed: {error}")
        _write_failsafe_evaluated("policy_evaluation", error)
        _exit(blocked=True, mode=mode)

    print("[gate] Layer 3: Generating human-readable pages...")
    try:
        generate_human_readable_outputs(
            repo_root=Path(".").resolve(),
            analysis_dir=ANALYSIS_DIR.resolve(),
            normalized_path=NORMALIZED_FILE.resolve(),
            evaluated_path=EVALUATED_FILE.resolve(),
        )
    except (OSError, ValueError, TypeError, KeyError, RuntimeError) as error:
        print(f"[gate] Human-readable report generation failed (non-fatal): {error}")

    _exit(blocked=blocked, mode=mode)


def _exit(blocked: bool, mode: str) -> None:
    """
    Apply gate.mode and exit with the correct status code.

    Parameters
    ----------
    blocked : bool
        True if any blocking tool violated its policy.
    mode : str
        Gate mode. Expected values are "enforce" or "report_only".

    Raises
    ------
    SystemExit
        Exit code 0 when merge is allowed.
        Exit code 1 when merge is blocked.
    """
    if mode == "report_only":
        if blocked:
            print("[gate] Violations detected. report_only mode; merge not blocked.")
        else:
            print("[gate] All checks passed (report_only mode).")
        raise SystemExit(0)

    if blocked:
        print("[gate] Merge blocked due to policy violations.")
        raise SystemExit(1)

    print("[gate] All checks passed. Merge approved.")
    raise SystemExit(0)


if __name__ == "__main__":
    main()
