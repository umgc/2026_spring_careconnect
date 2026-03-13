"""
Standardized Data Structures for the Quality Gate Engine.

This module defines the canonical data structures used across the
quality gate pipeline:

- normalize.py       (Layer 1)
- policy_engine.py   (Layer 2)
- gate.py            (Orchestration + reporting)
- humanize.py        (Human-readable findings)

Design Principle
----------------
This module defines **structure only**. It must never contain:

- Policy thresholds
- Enforcement logic
- Tool-specific parsing rules

All parsers must return data using this structure so that the policy
layer remains tool-agnostic and deterministic.

Compatibility Rules
-------------------
- Prefer stable, additive schema changes.
- Do not remove fields once adopted to maintain backward compatibility.

Security Note
-------------
Parsers must never store raw secrets or token values in findings
or metadata. Human-readable artifacts may be uploaded and viewed
by other users.
"""

from __future__ import annotations

from typing import Any, Dict


def base_tool_result(tool_name: str) -> Dict[str, Any]:
    """
    Create the standardized result structure returned by every tool parser.

    Parsers must always return this structure to ensure that downstream
    components (policy evaluation, normalization, and reporting) operate
    on a consistent and predictable schema.

    Requirements
    ------------
    - All parsers must return this structure.
    - Fields must not be removed.
    - Parsers should populate fields as accurately as possible.
    - Missing information should use safe defaults.

    Parameters
    ----------
    tool_name : str
        The canonical identifier for the tool producing the results.
        This value must match the identifiers used in:

        - policy.yaml
        - normalize.py registration
        - reporting output

    Returns
    -------
    Dict[str, Any]
        A dictionary representing the normalized result structure for
        the tool execution.
    """

    return {
        # ------------------------------------------------------
        # Tool identifier
        # ------------------------------------------------------
        "tool": tool_name,

        # ------------------------------------------------------
        # Execution + governance status
        # ------------------------------------------------------
        "artifact_present": False,
        "executed": False,
        "runtime_error": False,

        # ------------------------------------------------------
        # Findings (normalized)
        # ------------------------------------------------------
        "findings": [],

        # ------------------------------------------------------
        # Finding counts
        # ------------------------------------------------------
        "violation_count": 0,

        # ------------------------------------------------------
        # Severity breakdown
        # ------------------------------------------------------
        "severity_counts": {
            "critical": 0,
            "high": 0,
            "medium": 0,
            "low": 0,
            "info": 0,
        },

        # ------------------------------------------------------
        # Maximum severity encountered
        # ------------------------------------------------------
        "max_severity": None,

        # ------------------------------------------------------
        # Tool-specific metadata
        # ------------------------------------------------------
        "metadata": {},
    }
