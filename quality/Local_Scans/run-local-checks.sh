#!/bin/sh

# ==========================================================
# CareConnect Local Quality Gate — Entry Point
# ----------------------------------------------------------
# Orchestrates all local checks, generates the HTML report,
# packages everything into a zip, and opens the report.
#
# Usage:
#   sh quality/local/run-local-checks.sh
#
# Requires: java, mvn, python3 (or python/py), flutter
# ==========================================================

set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CHECKS_DIR="${SCRIPT_DIR}/checks"
REPORT_DIR="${SCRIPT_DIR}/report"
TOOLS_DIR="${SCRIPT_DIR}/tools"

# Ensure Python can resolve the repository package root.
export PYTHONPATH="${REPO_ROOT}"

# Resolve Python command across platforms (Linux/macOS/Windows Git Bash).
if command -v python3 >/dev/null 2>&1; then
  PYTHON_CMD="python3"
elif command -v python >/dev/null 2>&1; then
  PYTHON_CMD="python"
elif command -v py >/dev/null 2>&1; then
  PYTHON_CMD="py"
else
  echo "ERROR: Python not found. Install Python 3 and ensure one of python3/python/py is on PATH."
  exit 1
fi

run_python() {
  if [ "${PYTHON_CMD}" = "py" ]; then
    py -3 "$@"
  else
    "${PYTHON_CMD}" "$@"
  fi
}

TIMESTAMP="$(date '+%Y-%m-%d-%H%M%S')"
COMMIT_SHA="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || echo "no-git")"

unset TMPDIR
WORK_DIR="$(run_python -c "import tempfile; print(tempfile.mkdtemp())")"
ZIP_NAME="${TIMESTAMP}-${COMMIT_SHA}-local-quality-report.zip"
ZIP_PATH="${HOME}/Downloads/${ZIP_NAME}"
GENERATED_AT="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"
SCAN_USER="$(whoami)"

FAILED=0
FL_STATUS="skipped"
CS_STATUS="skipped"
PMD_STATUS="skipped"
SB_STATUS="skipped"

# ----------------------------------------------------------
# Print header
# ----------------------------------------------------------
echo ""
echo "=============================="
echo " CareConnect Local Gate Check"
echo "=============================="
echo ""

# ----------------------------------------------------------
# Run Flutter Analyze
# ----------------------------------------------------------
echo "--- Flutter Analyze ---"
if sh "${CHECKS_DIR}/check_flutter.sh" "${REPO_ROOT}" "${WORK_DIR}" "${TOOLS_DIR}"; then
  FL_STATUS="passed"
else
  FL_STATUS="failed"
  FAILED=$((FAILED + 1))
fi

# ----------------------------------------------------------
# Run Checkstyle
# ----------------------------------------------------------
echo ""
echo "--- Checkstyle ---"
if sh "${CHECKS_DIR}/check_checkstyle.sh" "${REPO_ROOT}" "${WORK_DIR}" "${TOOLS_DIR}"; then
  CS_STATUS="passed"
else
  CS_STATUS="failed"
  FAILED=$((FAILED + 1))
fi

# ----------------------------------------------------------
# Run PMD
# ----------------------------------------------------------
echo ""
echo "--- PMD ---"
if sh "${CHECKS_DIR}/check_pmd.sh" "${REPO_ROOT}" "${WORK_DIR}" "${TOOLS_DIR}"; then
  PMD_STATUS="passed"
else
  PMD_STATUS="failed"
  FAILED=$((FAILED + 1))
fi

# ----------------------------------------------------------
# Run SpotBugs
# ----------------------------------------------------------
echo ""
echo "--- SpotBugs ---"
if sh "${CHECKS_DIR}/check_spotbugs.sh" "${REPO_ROOT}" "${WORK_DIR}" "${TOOLS_DIR}"; then
  SB_STATUS="passed"
else
  SB_STATUS="failed"
  FAILED=$((FAILED + 1))
fi

# ----------------------------------------------------------
# Export environment for Python scripts
# ----------------------------------------------------------
export WORK_DIR
export ZIP_PATH
export REPO_ROOT
export GENERATED_AT
export SCAN_USER
export FL_STATUS
export CS_STATUS
export PMD_STATUS
export SB_STATUS
export FAILED

# ----------------------------------------------------------
# Generate HTML report
# ----------------------------------------------------------
echo ""
echo "Generating HTML report..."
run_python -m quality.Local_Scans.report.generate_report

# ----------------------------------------------------------
# Package zip
# ----------------------------------------------------------
echo ""
echo "Packaging report..."
run_python - <<'PY'
import os
import zipfile
from pathlib import Path

work_dir = Path(os.environ["WORK_DIR"])
zip_path = Path(os.environ["ZIP_PATH"])

zip_path.parent.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zip_file:
    for file_path in sorted(work_dir.rglob("*")):
        if file_path.is_file():
            zip_file.write(file_path, arcname=file_path.relative_to(work_dir))

print(f"[package] ZIP written to: {zip_path}")
PY

# ----------------------------------------------------------
# Open in browser
# ----------------------------------------------------------
echo ""
echo "Opening report in browser..."
run_python "${REPORT_DIR}/open_report.py"

# ----------------------------------------------------------
# Summary
# ----------------------------------------------------------
echo ""
echo "=============================="
echo " Results"
echo "=============================="

printf " Flutter       "
if [ "${FL_STATUS}" = "passed" ]; then
  printf "PASSED\n"
elif [ "${FL_STATUS}" = "failed" ]; then
  printf "FAILED\n"
else
  printf "SKIPPED\n"
fi

printf " Checkstyle    "
if [ "${CS_STATUS}" = "passed" ]; then
  printf "PASSED\n"
elif [ "${CS_STATUS}" = "failed" ]; then
  printf "FAILED\n"
else
  printf "SKIPPED\n"
fi

printf " PMD           "
if [ "${PMD_STATUS}" = "passed" ]; then
  printf "PASSED\n"
elif [ "${PMD_STATUS}" = "failed" ]; then
  printf "FAILED\n"
else
  printf "SKIPPED\n"
fi

printf " SpotBugs      "
if [ "${SB_STATUS}" = "passed" ]; then
  printf "PASSED\n"
elif [ "${SB_STATUS}" = "failed" ]; then
  printf "FAILED\n"
else
  printf "SKIPPED\n"
fi

echo "------------------------------"
if [ "${FAILED}" -eq 0 ]; then
  echo " Result: All checks passed"
else
  echo " Result: ${FAILED} tool(s) failed"
fi
echo "=============================="
echo ""
echo " Report saved to: ${ZIP_PATH}"
echo ""

exit "${FAILED}"
