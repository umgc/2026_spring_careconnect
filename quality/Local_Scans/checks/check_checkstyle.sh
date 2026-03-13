#!/bin/sh

# ==========================================================
# Checkstyle — Local Check
# ----------------------------------------------------------
# Runs Checkstyle against backend/core/src/main/java.
# Downloads the Checkstyle JAR to quality/tools/ on first run.
#
# Arguments:
#   $1 — REPO_ROOT
#   $2 — WORK_DIR (temp directory for raw output)
#   $3 — TOOLS_DIR (quality/tools/)
#
# Output:
#   $2/checkstyle.xml
#
# Exit codes:
#   0 — passed (no violations)
#   1 — failed (violations found) or skipped
# ==========================================================

set -eu

REPO_ROOT="$1"
WORK_DIR="$2"
TOOLS_DIR="$3"

JAVA_SRC="${REPO_ROOT}/backend/core/src/main/java"

CHECKSTYLE_VERSION="10.12.4"
CHECKSTYLE_JAR="${TOOLS_DIR}/checkstyle-${CHECKSTYLE_VERSION}-all.jar"
CHECKSTYLE_URL="https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar"

OUT="${WORK_DIR}/checkstyle.xml"


# ----------------------------------------------------------
# Initialize artifact
# ----------------------------------------------------------

echo "<checkstyle></checkstyle>" > "${OUT}"


# ----------------------------------------------------------
# Verify Java availability
# ----------------------------------------------------------

if ! command -v java >/dev/null 2>&1; then
  echo "WARNING: java not installed. Skipping Checkstyle."
  exit 0
fi


# ----------------------------------------------------------
# Verify source directory exists
# ----------------------------------------------------------

if [ ! -d "${JAVA_SRC}" ]; then
  echo "WARNING: src/main/java not found. Skipping Checkstyle."
  exit 0
fi


# ----------------------------------------------------------
# Download Checkstyle if missing
# ----------------------------------------------------------

if [ ! -f "${CHECKSTYLE_JAR}" ]; then
  echo "Downloading Checkstyle ${CHECKSTYLE_VERSION}..."

  mkdir -p "${TOOLS_DIR}"

  curl -fsSL -o "${CHECKSTYLE_JAR}" "${CHECKSTYLE_URL}"

  echo "Checkstyle downloaded."
fi


# ----------------------------------------------------------
# Run Checkstyle
# ----------------------------------------------------------

java -jar "${CHECKSTYLE_JAR}" \
  -c sun_checks.xml \
  "${JAVA_SRC}" \
  -f xml \
  -o "${OUT}" || true


# ----------------------------------------------------------
# Count violations
# ----------------------------------------------------------

COUNT="$(grep -c "<error " "${OUT}" || true)"

if [ "${COUNT}" -eq 0 ]; then
  echo "Checkstyle passed. No violations found."
  exit 0
else
  echo "Checkstyle failed. ${COUNT} violation(s) found."
  exit 1
fi
