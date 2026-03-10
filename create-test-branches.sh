#!/bin/bash
set -e

REPO="umgc/2026_spring_careconnect"
BASE_BRANCH="team_d"
REPO_ROOT="/Volumes/DevDrive/code/2026_spring_careconnect"
BACKEND_DIR="backend/core/src/main/java/com/careconnect/test"
FRONTEND_DIR="frontend/lib/test_violations"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== CareConnect CI/CD Test Branch Creator ===${NC}"

if [ ! -d "$REPO_ROOT/.git" ]; then
  echo -e "${RED}ERROR: Not in the repo root.${NC}"; exit 1
fi

cd "$REPO_ROOT"

CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" = "main" ]; then
  echo -e "${RED}ERROR: You are on main.${NC}"; exit 1
fi

GH_USER=$(gh api user --jq '.login' 2>/dev/null || echo "")
if [ -z "$GH_USER" ]; then
  echo -e "${RED}ERROR: gh CLI not authenticated.${NC}"; exit 1
fi

echo -e "${GREEN}✓ Repo confirmed | Branch: $CURRENT_BRANCH | gh: $GH_USER${NC}"

create_branch() {
  local BRANCH_NAME="$1"
  echo -e "${BLUE}─── Creating: $BRANCH_NAME ───${NC}"
  git branch -D "$BRANCH_NAME" 2>/dev/null || true
  git fetch origin team_d --quiet
  git checkout -b "$BRANCH_NAME" origin/team_d --quiet
  echo -e "${GREEN}✓ Branched from origin/team_d${NC}"
}

commit_and_push() {
  local BRANCH_NAME="$1"
  local COMMIT_MSG="$2"
  git add -A
  git commit -m "$COMMIT_MSG" --quiet
  git push origin "$BRANCH_NAME" --force --quiet
  echo -e "${GREEN}✓ Pushed $BRANCH_NAME${NC}"
  git checkout team_d-james-cicd-gating --quiet
}

open_pr() {
  local BRANCH_NAME="$1"
  local PR_TITLE="$2"
  local PR_BODY="$3"
  gh pr create --repo "$REPO" --base "$BASE_BRANCH" \
    --head "$BRANCH_NAME" --title "$PR_TITLE" --body "$PR_BODY"
  echo -e "${GREEN}✓ PR created for $BRANCH_NAME${NC}"
}

make_dirs() { mkdir -p "$BACKEND_DIR" "$FRONTEND_DIR"; }
cleanup() { rm -rf "$BACKEND_DIR" "$FRONTEND_DIR"; }

CLEAN_JAVA='package com.careconnect.test;
/**
 * Clean service class for CI/CD pipeline testing.
 */
public class CleanService {
    /** Service name. */
    private final String name;
    /**
     * Constructs a CleanService.
     * @param name the service name
     */
    public CleanService(final String name) { this.name = name; }
    /**
     * Returns a greeting.
     * @return greeting string
     */
    public String greet() { return "Hello from " + name; }
}'

CLEAN_DART='/// Clean Dart file. No violations.
class CleanService {
  final String name;
  const CleanService({required this.name});
  String greet() => '"'"'Hello from $name'"'"';
}'

# =============================================================================
# BRANCH 1: test/all-passing
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 1: test/all-passing ════${NC}"
create_branch "test/all-passing"
cleanup
make_dirs
echo "$CLEAN_JAVA" > "$BACKEND_DIR/CleanService.java"
echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"
commit_and_push "test/all-passing" "test: all-passing — clean code, no violations"
open_pr "test/all-passing" \
  "[CI TEST] All Tools Passing" \
  "## CI/CD Test — All Passing
All tools should pass. Do not merge — test branch only."

# =============================================================================
# BRANCH 2: test/all-failing
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 2: test/all-failing ════${NC}"
create_branch "test/all-failing"
cleanup
make_dirs

cat > "$BACKEND_DIR/BadService.java" << 'JAVA'
package com.careconnect.test;
import java.io.*;
import java.sql.*;
public class BadService {
    public static String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
    public String x;
    public void doEverything(String input) {
        String unused = "never used";
        String result = null;
        System.out.println(result.length());
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/db", "root", "password123");
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT * FROM users WHERE id = " + input);
        } catch (Exception e) {
        }
        try {
            FileInputStream fis = new FileInputStream("file.txt");
            int data = fis.read();
        } catch (IOException e) { e.printStackTrace(); }
        if (input != null) { if (input.length() > 0) { if (input.startsWith("a")) {
            if (input.endsWith("z")) { if (input.contains("m")) {
                System.out.println("deep nesting");
            } } } } }
    }
}
JAVA

cat > "$FRONTEND_DIR/bad_widget.dart" << 'DART'
import 'dart:io';
class BadService {
  final String apiKey = 'hardcoded-secret-key-12345';
  final String password = 'supersecretpassword';
  void doSomething() {
    var unused1 = 'never used';
    dynamic anything = 'value';
    print(anything.nonExistentMethod());
    var serverUrl = 'http://192.168.1.100:8080/api';
    var userId = Platform.environment['USER_INPUT'];
    var query = 'SELECT * FROM users WHERE id = $userId';
  }
}
DART

commit_and_push "test/all-failing" "test: all-failing — violations in every tool"
open_pr "test/all-failing" \
  "[CI TEST] All Tools Failing" \
  "## CI/CD Test — All Failing
All tools should fail. Merge should be BLOCKED. Do not merge — test branch only."

# =============================================================================
# BRANCH 3: test/fail-checkstyle
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 3: test/fail-checkstyle ════${NC}"
create_branch "test/fail-checkstyle"
cleanup
make_dirs

cat > "$BACKEND_DIR/CheckstyleViolation.java" << 'JAVA'
package com.careconnect.test;
public class CheckstyleViolation {
    public void BadMethodName() {
        int x = 12345678;
        System.out.println(x);
    }
    public void anotherBadMethodName() { int y = 99999999; System.out.println("This line is intentionally very long to trigger the line length checkstyle rule which is usually set to 100 or 120 characters"); }
}
JAVA

echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"
commit_and_push "test/fail-checkstyle" "test: only Checkstyle violations"
open_pr "test/fail-checkstyle" \
  "[CI TEST] Checkstyle Fails Only" \
  "## CI/CD Test — Checkstyle Only
Only Checkstyle should fail. Do not merge — test branch only."

# =============================================================================
# BRANCH 4: test/fail-spotbugs
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 4: test/fail-spotbugs ════${NC}"
create_branch "test/fail-spotbugs"
cleanup
make_dirs

cat > "$BACKEND_DIR/SpotBugsViolation.java" << 'JAVA'
package com.careconnect.test;
import java.io.FileInputStream;
import java.io.IOException;
/**
 * Demonstrates SpotBugs violations for CI/CD pipeline testing.
 */
public class SpotBugsViolation {
    /**
     * Triggers null dereference and resource leak.
     * @param input input string
     */
    public void riskyMethod(final String input) {
        String value = null;
        if (input.equals("trigger")) { value = "set"; }
        System.out.println(value.length());
        try {
            FileInputStream fis = new FileInputStream("test.txt");
            int data = fis.read();
            System.out.println(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed", e);
        }
    }
    /**
     * Triggers ignored return value.
     * @param str the string
     */
    public void ignoredReturn(final String str) {
        str.replace("old", "new");
    }
}
JAVA

echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"
commit_and_push "test/fail-spotbugs" "test: only SpotBugs violations"
open_pr "test/fail-spotbugs" \
  "[CI TEST] SpotBugs Fails Only" \
  "## CI/CD Test — SpotBugs Only
Only SpotBugs should fail. Do not merge — test branch only."

# =============================================================================
# BRANCH 5: test/fail-pmd
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 5: test/fail-pmd ════${NC}"
create_branch "test/fail-pmd"
cleanup
make_dirs

cat > "$BACKEND_DIR/PmdViolation.java" << 'JAVA'
package com.careconnect.test;
/**
 * Demonstrates PMD violations for CI/CD pipeline testing.
 */
public class PmdViolation {
    /**
     * Triggers empty catch block.
     * @param input input string
     */
    public void emptyCatch(final String input) {
        try {
            int value = Integer.parseInt(input);
            System.out.println(value);
        } catch (NumberFormatException e) {
        }
    }
    /**
     * Triggers unused variable and generic exception.
     * @param data the data
     */
    public void unusedVariable(final String data) {
        String unused = "never read";
        int anotherUnused = 42;
        try {
            System.out.println(data.toUpperCase());
        } catch (Exception e) {
            System.out.println("error");
        }
    }
    /**
     * Triggers cyclomatic complexity.
     * @param a first
     * @param b second
     * @param c third
     * @param d fourth
     * @return result
     */
    public int tooComplex(final int a, final int b, final int c, final int d) {
        if (a > 0) { if (b > 0) { if (c > 0) { if (d > 0) { return 1;
            } else if (d < -10) { return 2; } else { return 3; }
            } else if (c < -10) { return 4; } else { return 5; }
            } else if (b < -10) { return 6; } else { return 7; }
        } else if (a < -10) { return 8; } else { return 9; }
    }
}
JAVA

echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"
commit_and_push "test/fail-pmd" "test: only PMD violations"
open_pr "test/fail-pmd" \
  "[CI TEST] PMD Fails Only" \
  "## CI/CD Test — PMD Only
Only PMD should fail. Do not merge — test branch only."

# =============================================================================
# BRANCH 6: test/fail-semgrep
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 6: test/fail-semgrep ════${NC}"
create_branch "test/fail-semgrep"
cleanup
make_dirs

cat > "$BACKEND_DIR/SemgrepViolation.java" << 'JAVA'
package com.careconnect.test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
/**
 * Demonstrates Semgrep violations for CI/CD pipeline testing.
 */
public class SemgrepViolation {
    /** Hardcoded password. */
    private static final String DB_PASSWORD = "hardcoded_db_password_123";
    /**
     * Triggers SQL injection.
     * @param userId raw user input
     * @throws Exception on error
     */
    public void sqlInjection(final String userId) throws Exception {
        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost/careconnect", "root", DB_PASSWORD);
        Statement stmt = conn.createStatement();
        stmt.execute("SELECT * FROM patients WHERE id = " + userId);
        conn.close();
    }
    /**
     * Triggers weak hash.
     * @param data data to hash
     * @return hash bytes
     * @throws Exception on error
     */
    public byte[] weakHash(final String data) throws Exception {
        java.security.MessageDigest md =
            java.security.MessageDigest.getInstance("MD5");
        return md.digest(data.getBytes());
    }
}
JAVA

cat > "$FRONTEND_DIR/semgrep_violation.dart" << 'DART'
/// Dart file with Semgrep violations for CI/CD testing.
class SemgrepViolation {
  final String apiSecret = 'hardcoded-api-secret-key-abc123';
  String getEndpoint() {
    return 'http://10.0.0.1:8080/api/patients';
  }
}
DART

commit_and_push "test/fail-semgrep" "test: only Semgrep violations"
open_pr "test/fail-semgrep" \
  "[CI TEST] Semgrep Fails Only" \
  "## CI/CD Test — Semgrep Only
Only Semgrep should fail. Do not merge — test branch only."

# =============================================================================
# BRANCH 7: test/fail-flutter-analyze
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 7: test/fail-flutter-analyze ════${NC}"
create_branch "test/fail-flutter-analyze"
cleanup
make_dirs

echo "$CLEAN_JAVA" > "$BACKEND_DIR/CleanService.java"

cat > "$FRONTEND_DIR/flutter_analyze_violation.dart" << 'DART'
import 'dart:io';
import 'dart:convert';
class FlutterAnalyzeViolation {
  String name;
  FlutterAnalyzeViolation(this.name);
  void doSomething() {
    print('This triggers avoid_print lint rule');
    String unused = 'never read';
    if (name is String) { print(name); }
    String doubleQuoted = "should use single quotes";
    print(doubleQuoted);
  }
}
DART

commit_and_push "test/fail-flutter-analyze" "test: only flutter analyze violations"
open_pr "test/fail-flutter-analyze" \
  "[CI TEST] Flutter Analyze Fails Only" \
  "## CI/CD Test — Flutter Analyze Only
Only flutter analyze should fail. Do not merge — test branch only."

# =============================================================================
# BRANCH 8: test/fail-trufflehog
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 8: test/fail-trufflehog ════${NC}"
create_branch "test/fail-trufflehog"
cleanup
make_dirs

echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"

cat > "$BACKEND_DIR/LegacyConfig.java" << 'JAVA'
package com.careconnect.test;
/**
 * Legacy config with fake credentials for TruffleHog testing.
 * DO NOT use in real code.
 */
public class LegacyConfig {
    private static final String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    /**
     * Returns the AWS key.
     * @return aws key string
     */
    public String getAwsKey() { return AWS_KEY; }
}
JAVA

commit_and_push "test/fail-trufflehog" "test: TruffleHog detection — fake AWS key patterns"
open_pr "test/fail-trufflehog" \
  "[CI TEST] TruffleHog Fails Only" \
  "## CI/CD Test — TruffleHog Only
TruffleHog should detect fake AWS key patterns. Do not merge — test branch only."

# =============================================================================
# BRANCH 9: test/fail-owasp
# =============================================================================
echo -e "\n${BLUE}════ BRANCH 9: test/fail-owasp ════${NC}"
create_branch "test/fail-owasp"
cleanup
make_dirs

echo "$CLEAN_JAVA" > "$BACKEND_DIR/CleanService.java"
echo "$CLEAN_DART" > "$FRONTEND_DIR/clean_widget.dart"

if [ -f "backend/core/pom.xml" ]; then
  cp backend/core/pom.xml backend/core/pom.xml.bak
  sed -i '' 's|</dependencies>|        <!-- TEST ONLY: CVE-2021-44228 Log4Shell CVSS 10.0 REMOVE AFTER TESTING -->\n        <dependency>\n            <groupId>org.apache.logging.log4j</groupId>\n            <artifactId>log4j-core</artifactId>\n            <version>2.14.1</version>\n        </dependency>\n    </dependencies>|' backend/core/pom.xml
fi

commit_and_push "test/fail-owasp" "test: only OWASP failure — log4j 2.14.1 CVE-2021-44228"

if [ -f "backend/core/pom.xml.bak" ]; then
  mv backend/core/pom.xml.bak backend/core/pom.xml
fi

open_pr "test/fail-owasp" \
  "[CI TEST] OWASP Dependency-Check Fails Only" \
  "## CI/CD Test — OWASP Only
OWASP should detect log4j 2.14.1 CVE-2021-44228 CVSS 10.0. Do not merge — test branch only."

# =============================================================================
# DONE
# =============================================================================
echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  All 9 test branches created and PRs opened!           ${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Go to github.com/umgc/2026_spring_careconnect/pulls"
echo "  2. Open each PR and watch the workflow run"
echo "  3. Check the report comment on each PR"
echo ""
echo -e "${YELLOW}To clean up when done:${NC}"
echo "  git branch -D test/all-passing test/all-failing test/fail-checkstyle \\"
echo "    test/fail-spotbugs test/fail-pmd test/fail-semgrep \\"
echo "    test/fail-flutter-analyze test/fail-trufflehog test/fail-owasp"
echo "  git push origin --delete test/all-passing test/all-failing test/fail-checkstyle \\"
echo "    test/fail-spotbugs test/fail-pmd test/fail-semgrep \\"
echo "    test/fail-flutter-analyze test/fail-trufflehog test/fail-owasp"
