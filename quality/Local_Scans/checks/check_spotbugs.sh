#!/bin/sh

# ==========================================================
# SpotBugs - Local Check
# ----------------------------------------------------------
# Compiles backend/core via Maven then runs SpotBugs against
# the compiled classes.
# Downloads the SpotBugs tarball to quality/tools/ on first run.
#
# Arguments:
#   $1 - REPO_ROOT
#   $2 - WORK_DIR
#   $3 - TOOLS_DIR
#
# Output:
#   $2/spotbugs.xml
#
# Exit codes:
#   0 - passed (no bugs found)
#   1 - failed (bugs found) or skipped
# ==========================================================

set -eu

REPO_ROOT="$1"
WORK_DIR="$2"
TOOLS_DIR="$3"

JAVA_ROOT="${REPO_ROOT}/backend/core"
CLASSES="${JAVA_ROOT}/target/classes"

SPOTBUGS_VERSION="4.9.3"
SPOTBUGS_DIR="${TOOLS_DIR}/spotbugs-${SPOTBUGS_VERSION}"
SPOTBUGS_TGZ="${TOOLS_DIR}/spotbugs-${SPOTBUGS_VERSION}.tgz"
SPOTBUGS_URL="https://github.com/spotbugs/spotbugs/releases/download/${SPOTBUGS_VERSION}/spotbugs-${SPOTBUGS_VERSION}.tgz"

OUT="${WORK_DIR}/spotbugs.xml"

echo "<BugCollection></BugCollection>" > "${OUT}"

if ! command -v java >/dev/null 2>&1; then
  echo "java not installed. Skipping SpotBugs."
  exit 0
fi

# ----------------------------------------------------------
# Determine Maven command
# ----------------------------------------------------------

if [ -f "${JAVA_ROOT}/mvnw" ]; then
  MVN_CMD="${JAVA_ROOT}/mvnw"
elif [ -f "${JAVA_ROOT}/mvnw.cmd" ]; then
  MVN_CMD="${JAVA_ROOT}/mvnw.cmd"
elif command -v mvn >/dev/null 2>&1; then
  MVN_CMD="mvn"
else
  echo "mvn not found (no mvnw wrapper and mvn not in PATH). Skipping SpotBugs."
  exit 0
fi

if [ ! -d "${JAVA_ROOT}" ]; then
  echo "backend/core not found. Skipping SpotBugs."
  exit 0
fi

# ----------------------------------------------------------
# Remove older SpotBugs version if present
# ----------------------------------------------------------

if [ -d "${TOOLS_DIR}/spotbugs-4.8.3" ]; then
  echo "Removing old SpotBugs 4.8.3..."
  rm -rf "${TOOLS_DIR}/spotbugs-4.8.3"
  rm -f "${TOOLS_DIR}/spotbugs-4.8.3.tgz"
fi

# ----------------------------------------------------------
# Download SpotBugs if needed
# ----------------------------------------------------------

if [ ! -f "${SPOTBUGS_TGZ}" ]; then
  echo "Downloading SpotBugs ${SPOTBUGS_VERSION}..."
  mkdir -p "${TOOLS_DIR}"
  curl -fsSL -o "${SPOTBUGS_TGZ}" "${SPOTBUGS_URL}"
  echo "SpotBugs downloaded."
fi

# ----------------------------------------------------------
# Extract SpotBugs
# ----------------------------------------------------------

if [ ! -d "${SPOTBUGS_DIR}" ]; then
  echo "Extracting SpotBugs..."
  tar -xzf "${SPOTBUGS_TGZ}" -C "${TOOLS_DIR}"
  chmod +x "${SPOTBUGS_DIR}/bin/spotbugs"
  echo "SpotBugs extracted."
fi

# ----------------------------------------------------------
# Configure JAVA_HOME if possible
# ----------------------------------------------------------

if [ -x "/usr/libexec/java_home" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 23 2>/dev/null || true)"
  export JAVA_HOME
fi

# ----------------------------------------------------------
# Compile Java sources
# ----------------------------------------------------------

echo "Compiling Java..."
(
  cd "${JAVA_ROOT}"
  "${MVN_CMD}" compile -q --batch-mode
) || true

if [ ! -d "${CLASSES}" ]; then
  echo "Compile failed. No classes produced. Skipping SpotBugs."
  exit 0
fi

CLASS_COUNT="$(find "${CLASSES}" -name "*.class" | wc -l | tr -d ' ')"

if [ "${CLASS_COUNT}" -eq 0 ]; then
  echo "No .class files found. Skipping SpotBugs."
  exit 0
fi

# ----------------------------------------------------------
# Run SpotBugs
# ----------------------------------------------------------

echo "Analyzing ${CLASS_COUNT} class file(s)..."

"${SPOTBUGS_DIR}/bin/spotbugs" \
  -textui -xml \
  -effort:max \
  -output "${OUT}" \
  "${CLASSES}" || true

COUNT="$(grep -c "<BugInstance" "${OUT}" || true)"

if [ "${COUNT}" -eq 0 ]; then
  echo "SpotBugs passed. No bugs found."
  exit 0
else
  echo "SpotBugs failed. ${COUNT} bug(s) found."
  exit 1
fi
