# CareConnect Local Quality Gate

A local pre-commit quality gate that runs static analysis tools against
`backend/core/` before every commit. If any tool finds violations, the
commit is blocked. A timestamped HTML report and raw tool outputs are
zipped and saved to `~/Downloads/` on every run.

---

## Prerequisites

The following must be installed on your machine before running the gate:
| Dependency | Purpose | Install |
|------------|----------------------------------|----------------------------------|
| `java` | Run Checkstyle, PMD, SpotBugs | https://adoptium.net |
| `mvn` | Compile Java for SpotBugs | https://maven.apache.org |
| `python3` | Generate report, package zip | https://www.python.org |
| `curl` | Download tools on first run | Pre-installed on Mac/Git Bash |
| `unzip` | Extract PMD zip on first run | Pre-installed on Mac/Git Bash |
Tool JARs (Checkstyle, PMD, SpotBugs) are downloaded automatically to
`quality/local/tools/` on first run. No manual installation required.

---

## One-Time Setup

Run this once after cloning the repo:

```bash
git config core.hooksPath quality/local/hooks

```
