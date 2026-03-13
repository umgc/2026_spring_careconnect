"""
Shared Utility Functions for the Quality Gate Engine.

Purpose
-------
Provides common helper functions used across the gate engine,
including parsers, the normalizer, the policy engine, and reporting.

Design Principle
----------------
This module contains **logic only**. It must not contain schema
definitions, policy thresholds, or tool-specific parsing rules.

Responsibilities are separated as follows:

- Schema structure belongs in `schemas.py`
- Policy thresholds belong in `policy.yaml`
- Tool-specific parsing logic belongs in `parsers/`

Consumers
---------
These utilities are used by multiple components of the gate engine:

- quality/ci/gate/parsers/*.py        (severity resolution)
- quality/ci/gate/normalize.py        (severity resolution)
- quality/ci/gate/policy_engine.py    (severity comparison)
"""

from __future__ import annotations


# ----------------------------------------------------------
# Normalized severity vocabulary (in priority order)
# ----------------------------------------------------------
# This is the canonical severity order used across the entire
# gate engine. All tools map their native severities into this
# vocabulary via their individual SEVERITY_MAP definitions.
#
# Order: critical (most severe) → info (least severe)
# ----------------------------------------------------------

SEVERITY_ORDER = ["critical", "high", "medium", "low", "info"]


def determine_max_severity(severity_counts: dict) -> str | None:
    """
    Determine the highest severity level present in a set of findings.

    The function evaluates severity counts using the canonical priority
    order defined in ``SEVERITY_ORDER``:

        critical → high → medium → low → info

    This function acts as the **single source of truth** for resolving
    maximum severity across the entire quality gate system. Individual
    parser modules must not duplicate this logic.

    Parameters
    ----------
    severity_counts : dict
        Mapping of normalized severity labels to integer counts.
        Expected keys are: ``critical``, ``high``, ``medium``, ``low``,
        and ``info``. Missing keys are treated as zero.

    Returns
    -------
    str | None
        The highest severity label that has a non-zero count,
        or ``None`` if no findings are present.

    Examples
    --------
    >>> determine_max_severity({"critical": 0, "high": 3, "medium": 1, "low": 0, "info": 0})
    'high'

    >>> determine_max_severity({"critical": 0, "high": 0, "medium": 0, "low": 0, "info": 0})
    None
    """
    for level in SEVERITY_ORDER:
        if severity_counts.get(level, 0) > 0:
            return level

    return None


def is_severity_at_least(severity: str | None, threshold: str) -> bool:
    """
    Determine whether a severity level meets or exceeds a threshold.

    The comparison uses the canonical severity order defined in
    ``SEVERITY_ORDER``. Lower index values represent higher severity.

    This helper is typically used by the policy engine to enforce rules
    such as "medium_and_above".

    Parameters
    ----------
    severity : str | None
        A normalized severity label or ``None``.

    threshold : str
        A normalized severity label used as the comparison threshold.

    Returns
    -------
    bool
        ``True`` if the severity is equal to or more severe than the
        threshold. Returns ``False`` if severity is ``None`` or if the
        severity label is unrecognized.

    Examples
    --------
    >>> is_severity_at_least("high", "medium")
    True

    >>> is_severity_at_least("low", "high")
    False

    >>> is_severity_at_least(None, "low")
    False
    """
    if severity is None:
        return False

    try:
        severity_index = SEVERITY_ORDER.index(severity)
        threshold_index = SEVERITY_ORDER.index(threshold)
    except ValueError:
        # Unknown severity labels are treated as not meeting the threshold
        return False

    return severity_index <= threshold_index
