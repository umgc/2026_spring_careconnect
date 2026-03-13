#!/bin/sh

# ==========================================================
# Flutter Analyze — Local Check
# ----------------------------------------------------------
# Runs flutter analyze against the frontend/ directory.
# Flutter must be installed and available on PATH.
#
# Arguments:
#   $1 — REPO_ROOT
#   $2 — WORK_DIR
#   $3 — TOOLS_DIR (unused)
#
# Output:
#   $2/flutter_analyze.txt
#
# Exit codes:
#   0 — passed (no errors found)
#   1 — failed (errors found) or skipped
# ==========================================================

set -eu

REPO_ROOT="$1"
WORK_DIR="$2"
TOOLS_DIR="$3"  # unused, kept for interface consistency

FLUTTER_ROOT="${REPO_ROOT}/frontend"
OUT="${WORK_DIR}/flutter_analyze.txt"

# Initialize output artifact
: > "${OUT}"

# Verify Flutter availability
if ! command -v flutter >/dev/null 2>&1; then
  echo "Flutter not installed. Skipping Flutter Analyze."
  exit 0
fi

# Verify frontend directory exists
if [ ! -d "${FLUTTER_ROOT}" ]; then
  echo "frontend/ directory not found. Skipping Flutter Analyze."
  exit 0
fi

echo "Running Flutter Analyze..."

(
  cd "${FLUTTER_ROOT}"
  flutter analyze 2>&1
) > "${OUT}" || true

# Count analyzer errors
COUNT="$(grep -cE "^[[:space:]]*error[[:space:]]+[[:alnum:]_.]+" "${OUT}" || true)"

if [ "${COUNT}" -eq 0 ]; then
  echo "Flutter Analyze passed. No errors found."
  exit 0
else
  echo "Flutter Analyze failed. ${COUNT} error(s) found."
  exit 1
fi
