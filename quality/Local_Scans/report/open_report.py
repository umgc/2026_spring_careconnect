"""
Open Local Report in Browser

Opens the generated HTML report in the system default
browser. Works on Mac, Windows (Git Bash), and Linux.

Environment variables (set by run-local-checks.sh):
   WORK_DIR — temp directory containing local-report.html
"""

import os
import webbrowser
from pathlib import Path

# ----------------------------------------------------------
# Environment
# ----------------------------------------------------------
WORK_DIR = os.environ["WORK_DIR"]
REPORT = Path(WORK_DIR) / "local-report.html"

# ----------------------------------------------------------
# Open in browser
# ----------------------------------------------------------
if not REPORT.exists():
    print(f"[open-report] Report not found at: {REPORT}")
else:
    url = REPORT.as_uri()
    webbrowser.open(url)
    print(f"[open-report] Opened in browser: {url}")
