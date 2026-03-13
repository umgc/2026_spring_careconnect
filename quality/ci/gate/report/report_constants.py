"""
Shared constants for the report package.

This module centralizes shared constants used by the reporting layer,
including tool category mappings, severity color definitions, and
common Markdown table fragments used across report generators.
"""

_SECRETS      = "Secrets Scan"
_SAST_JAVA    = "SAST — Java"
_SAST_MULTI   = "SAST — Multi"
_SAST_FLUTTER = "SAST — Flutter"
_SAST_PYTHON  = "SAST — Python"
_SAST_WEB     = "SAST — Web"
_SCA_MULTI    = "SCA — Multi"
_SCA_CONTAINER = "SCA — Container"
_QUALITY_GATE = "Quality Gate"


CATEGORY_MAP: dict[str, str] = {
    "trufflehog":       _SECRETS,
    "gitleaks":         _SECRETS,
    "flutter_analyze":  _SAST_FLUTTER,
    "checkstyle":       _SAST_JAVA,
    "pmd":              _SAST_JAVA,
    "spotbugs":         _SAST_JAVA,
    "semgrep":          _SAST_MULTI,
    "pylint":           _SAST_PYTHON,
    "bandit":           _SAST_PYTHON,
    "htmlhint":         _SAST_WEB,
    "stylelint":        _SAST_WEB,
    "dependency_check": _SCA_MULTI,
    "trivy":            _SCA_CONTAINER,
}


SEVERITY_COLORS: dict[str, str] = {
    "critical": "#7c0000",
    "high":     "#c0392b",
    "medium":   "#e67e22",
    "low":      "#f1c40f",
    "info":     "#3498db",
}


_MD_TABLE_HEADER    = "| Field | Value |"
_MD_TABLE_SEPARATOR = "|-------|-------|"
