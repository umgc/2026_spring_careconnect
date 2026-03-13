#!/bin/sh

# ==========================================================
# PMD - Local Check
# ----------------------------------------------------------
# Runs PMD against backend/core/src/main/java.
# Downloads the PMD zip to quality/tools/ on first run.
#
# Arguments:
#   $1 - REPO_ROOT
#   $2 - WORK_DIR
#   $3 - TOOLS_DIR
#
# Output:
#   $2/pmd.xml
#
# Exit codes:
#   0 - passed (no violations)
#   1 - failed (violations found) or skipped
# ==========================================================

set -eu

REPO_ROOT="$1"
WORK_DIR="$2"
TOOLS_DIR="$3"

JAVA_SRC="${REPO_ROOT}/backend/core/src/main/java"

PMD_VERSION="6.55.0"
PMD_DIR="${TOOLS_DIR}/pmd-bin-${PMD_VERSION}"
PMD_ZIP="${TOOLS_DIR}/pmd-${PMD_VERSION}.zip"
PMD_URL="https://github.com/pmd/pmd/releases/download/pmd_releases/${PMD_VERSION}/pmd-bin-${PMD_VERSION}.zip"

OUT="${WORK_DIR}/pmd.xml"

echo "<pmd></pmd>" > "${OUT}"

if ! command -v java >/dev/null 2>&1; then
  echo "java not installed. Skipping PMD."
  exit 0
fi

if [ ! -d "${JAVA_SRC}" ]; then
  echo "src/main/java not found. Skipping PMD."
  exit 0
fi

if [ ! -f "${PMD_ZIP}" ]; then
  echo "Downloading PMD ${PMD_VERSION}..."
  mkdir -p "${TOOLS_DIR}"
  curl -fsSL -o "${PMD_ZIP}" "${PMD_URL}"
  echo "PMD downloaded."
fi

if [ ! -d "${PMD_DIR}" ]; then
  echo "Extracting PMD..."
  unzip -q "${PMD_ZIP}" -d "${TOOLS_DIR}"
  echo "PMD extracted."
fi

"${PMD_DIR}/bin/run.sh" pmd \
  -d "${JAVA_SRC}" \
  --rulesets category/java/bestpractices.xml,category/java/errorprone.xml,category/java/codestyle.xml \
  -f xml \
  -r "${OUT}" || true

COUNT="$(grep -c "<violation" "${OUT}" || true)"

if [ "${COUNT}" -eq 0 ]; then
  echo "PMD passed. No violations found."
  exit 0
else
  echo "PMD failed. ${COUNT} violation(s) found."
  exit 1
fi
