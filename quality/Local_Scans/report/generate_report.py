"""
Generate Local HTML Report — Entry Point

Reads parsed results from report_parsers.py and builds
the HTML report via report_html.py.
Writes the completed report to WORK_DIR/local-report.html.

Environment variables (set by run-local-checks.sh):
  WORK_DIR       — temp directory containing raw artifacts
  REPO_ROOT      — repository root path
  GENERATED_AT   — UTC timestamp string
  SCAN_USER      — local username
  FL_STATUS      — passed | failed | skipped
  CS_STATUS      — passed | failed | skipped
  PMD_STATUS     — passed | failed | skipped
  SB_STATUS      — passed | failed | skipped
  FAILED         — number of failed tools
"""

import os
from pathlib import Path
from quality.Local_Scans.report.report_html import build_html
from quality.Local_Scans.report.report_parsers import (
    parse_checkstyle,
    parse_flutter,
    parse_pmd,
    parse_spotbugs,
)

# ----------------------------------------------------------
# Environment
# ----------------------------------------------------------
WORK_DIR = os.environ["WORK_DIR"]
REPO_ROOT = os.environ["REPO_ROOT"]
GENERATED_AT = os.environ["GENERATED_AT"]
SCAN_USER = os.environ["SCAN_USER"]

FL_STATUS = os.environ["FL_STATUS"]
CS_STATUS = os.environ["CS_STATUS"]
PMD_STATUS = os.environ["PMD_STATUS"]
SB_STATUS = os.environ["SB_STATUS"]

FAILED = int(os.environ["FAILED"])

# ----------------------------------------------------------
# Input artifacts
# ----------------------------------------------------------
RAW_FL = Path(WORK_DIR) / "flutter_analyze.txt"
RAW_CS = Path(WORK_DIR) / "checkstyle.xml"
RAW_PMD = Path(WORK_DIR) / "pmd.xml"
RAW_SB = Path(WORK_DIR) / "spotbugs.xml"

OUT = Path(WORK_DIR) / "local-report.html"

# ----------------------------------------------------------
# Parse raw artifacts
# ----------------------------------------------------------
fl_findings, fl_sev = parse_flutter(RAW_FL)
cs_findings, cs_sev = parse_checkstyle(RAW_CS, REPO_ROOT)
pmd_findings, pmd_sev = parse_pmd(RAW_PMD, REPO_ROOT)
sb_findings, sb_sev = parse_spotbugs(RAW_SB)

# ----------------------------------------------------------
# Build HTML
# ----------------------------------------------------------
html = build_html(
    {
        "generated_at": GENERATED_AT,
        "scan_user": SCAN_USER,
        "repo_root": REPO_ROOT,
        "failed": FAILED,
        "fl_status": FL_STATUS,
        "cs_status": CS_STATUS,
        "pmd_status": PMD_STATUS,
        "sb_status": SB_STATUS,
        "fl_findings": fl_findings,
        "cs_findings": cs_findings,
        "pmd_findings": pmd_findings,
        "sb_findings": sb_findings,
        "fl_sev": fl_sev,
        "cs_sev": cs_sev,
        "pmd_sev": pmd_sev,
        "sb_sev": sb_sev,
    }
)

# ----------------------------------------------------------
# Write output
# ----------------------------------------------------------
OUT.write_text(html, encoding="utf-8")
print(f"[generate-report] HTML written to: {OUT}")
