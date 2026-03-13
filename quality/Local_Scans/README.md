# CareConnect Local Quality Gate

A local pre-commit quality gate that runs static analysis tools against
the CareConnect codebase before every commit. If any enforced tool finds
violations, the commit is blocked. A timestamped HTML report and raw tool
outputs are zipped and saved to `~/Downloads/` on every run.

---

## Tools

| Tool | Language | Category | Role |
|------|----------|----------|------|
| Flutter Analyze | Dart | SAST — Flutter | Enforced |
| Checkstyle | Java | SAST — Java | Enforced |
| PMD | Java | SAST — Java | Enforced |
| SpotBugs | Java | SAST — Java | Enforced |

All four tools are enforced. A violation in any tool will block the commit.

---

## Prerequisites

The following must be installed on your machine before running the gate:

| Dependency | Purpose | Install |
|------------|---------|---------|
| `java` | Run Checkstyle, PMD, SpotBugs | https://adoptium.net |
| `mvn` | Compile Java for SpotBugs | https://maven.apache.org |
| `flutter` | Run Flutter Analyze | https://flutter.dev |
| `python3` | Generate report, package zip | https://www.python.org |

Tool JARs (Checkstyle, PMD, SpotBugs) are pre-committed to
`quality/Local_Scans/tools/` — no manual download required.

> **macOS note:** If you have multiple Java versions installed, ensure
> Java 17 is active before running:
> ```bash
> export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
> export PATH="$JAVA_HOME/bin:$PATH"
> ```

> **Windows note:** Install Eclipse Temurin JDK 17, set `JAVA_HOME` in
> your environment variables, and add `%JAVA_HOME%\bin` to `Path`.

---

## One-Time Setup

Run this once after cloning the repo to enable the pre-commit hook:

```bash
git config core.hooksPath quality/Local_Scans/hooks
```

This registers the pre-commit hook so the gate runs automatically before
every `git commit`.

---

## Running Manually

To run the gate manually at any time without committing:

**Mac / Linux:**
```bash
sh quality/Local_Scans/run-local-checks.sh
```

**Windows (Git Bash):**
```
quality\Local_Scans\run-local-checks.bat
```

---

## Output

Every run produces:

| Output | Location | Description |
|--------|----------|-------------|
| HTML report | Opened automatically in browser | Full findings report with severity breakdown |
| ZIP archive | `~/Downloads/<timestamp>-<sha>-local-quality-report.zip` | HTML report + raw tool outputs |

The report includes:
- Pass/fail status per tool
- Severity breakdown (Critical, High, Medium, Low, Info)
- Full findings table per tool (file, line, rule, message)

---

## Directory Structure

```
quality/Local_Scans/
├── checks/                  Individual tool check scripts
│   ├── check_flutter.sh
│   ├── check_checkstyle.sh
│   ├── check_pmd.sh
│   └── check_spotbugs.sh
├── hooks/                   Git hook definitions
│   └── pre-commit           Registered via git config core.hooksPath
├── report/                  Report generation modules
│   ├── generate_report.py   Entry point — reads artifacts, builds HTML
│   ├── report_html.py       HTML report builder
│   ├── report_parsers.py    Raw artifact parsers
│   └── open_report.py       Opens report in browser
├── tools/                   Pre-committed tool binaries and JARs
│   ├── checkstyle-10.12.4-all.jar
│   ├── pmd-bin-6.55.0/
│   └── spotbugs-4.9.3/
├── Static_Analysis_Reports/ Local scan output history
├── run-local-checks.sh      Entry point (Mac / Linux)
└── run-local-checks.bat     Entry point (Windows)
```

---

## Troubleshooting

**Permission denied on PMD or SpotBugs binary:**
```bash
chmod +x quality/Local_Scans/tools/pmd-bin-6.55.0/bin/run.sh
chmod +x quality/Local_Scans/tools/spotbugs-4.9.3/bin/spotbugs
```

**Flutter not found:**
Ensure Flutter is installed and on your PATH:
```bash
flutter --version
```

**Java version mismatch (build fails):**
Ensure Java 17 is active. See macOS and Windows notes in Prerequisites.

**Report not opening in browser:**
The HTML report is saved to your system temp directory. Check the path
printed at the end of the run and open it manually in your browser.

---

## Relationship to CI Gate

This local gate is **BN1** — it runs on your machine before you push.

The CI gate engine (`quality/ci/gate/`) is **BN2** — it runs in GitHub
Actions on every push to `team_d` and `team_d-*` branches. The two
systems are complementary:

| | Local Gate (BN1) | CI Gate (BN2) |
|-|-----------------|---------------|
| When | Before commit | On push |
| Tools | Flutter, Checkstyle, PMD, SpotBugs | 13 tools (SAST, SCA, Secrets) |
| Output | HTML report + ZIP | Artifact bundle + PR comment |
| Enforcement | Blocks commit | Blocks merge |

---