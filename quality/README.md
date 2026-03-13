# CareConnect Quality & Security Enforcement Subsystem

## Overview

The `quality/` directory contains the automated quality and security
enforcement subsystem for CareConnect. It is organized into two
complementary systems that work together to enforce code quality and
security standards at every stage of the development workflow.

|System                      |Location              |When It Runs                                    |
|----------------------------|----------------------|------------------------------------------------|
|Local Quality Gate (BN1)    |`quality/Local_Scans/`|Before every commit, on the developer’s machine |
|CI Quality Gate Engine (BN2)|`quality/ci/gate/`    |On every push and pull request in GitHub Actions|

-----

## Local Quality Gate (BN1)

The local gate runs static analysis tools on the developer’s machine
before every `git commit`. If any enforced tool finds violations, the
commit is blocked. A timestamped HTML report and ZIP archive are
generated on every run.

**Tools:**
Flutter Analyze, Checkstyle, PMD, SpotBugs

**Entry points:**

```bash
# Mac / Linux
sh quality/Local_Scans/run-local-checks.sh

# Windows
quality\Local_Scans\run-local-checks.bat
```

See `quality/Local_Scans/README.md` for full setup and usage
instructions.

-----

## CI Quality Gate Engine (BN2)

The CI gate engine runs automatically on every push and pull request
via GitHub Actions. It executes 13 static analysis, security, and
vulnerability scanning tools, evaluates results against a centralized
policy, and enforces merge decisions via exit codes. No manual
intervention is required.

**Tools:**
TruffleHog, Gitleaks, Flutter Analyze, Checkstyle, PMD, SpotBugs,
Semgrep, Pylint, Bandit, HTMLHint, Stylelint, OWASP Dependency-Check,
Trivy

**Trigger:**
Every push and pull request to `team_d` and `team_d-*` branches.

**Artifact output:**

```
quality/analysis/
├── raw/           Native tool outputs (evidence layer)
├── normalized/    normalized.json — unified schema
├── evaluated/     evaluated.json — policy decisions
├── report.md      Markdown report (GitHub Actions Job Summary)
└── report.html    Full HTML report with per-tool findings
```

See `quality/ci/gate/README.md` for full configuration and usage
instructions.

-----

## How They Work Together

The two systems form a layered enforcement strategy:

```
Developer machine                   GitHub Actions
─────────────────                   ──────────────
git commit                          git push
     │                                   │
     ▼                                   ▼
Local Gate (BN1)                   CI Gate (BN2)
Flutter, Checkstyle,               13 tools including
PMD, SpotBugs                      secrets, SCA, SAST
     │                                   │
     ▼                                   ▼
Blocks commit                      Blocks merge
if violations found                if policy violated
```

The local gate catches common issues early before they reach CI. The CI
gate provides comprehensive coverage including secrets detection,
dependency vulnerabilities, and container scanning that are impractical
to run locally on every commit.

-----

## Subsystem Responsibilities

This subsystem is responsible for:

- Secrets detection
- Static analysis (SAST)
- Software composition analysis (SCA)
- Policy evaluation and enforcement
- Merge approval and block decisions
- Artifact generation and reporting

-----

## Architecture Principles

Changes to either system must:

- Preserve deterministic enforcement behavior
- Maintain raw → normalized → evaluated traceability in the CI gate
- Keep policy logic centralized in `quality/ci/gate/policy.yaml`
- Not introduce conditional bypass logic
- Ensure the CI gate scans real application code only (`SCAN_ROOT=.`)

-----

## Ownership

This subsystem is owned by Team D.

- **BN1 (Local Gate):** Changes must preserve local enforcement
  behavior and report parity with the CI gate report style.
- **BN2 (CI Gate):** Changes must preserve the gate engine architecture.
  Tool additions follow the five-step process documented in
  `quality/ci/gate/README.md`.
- **Policy changes** are made exclusively in
  `quality/ci/gate/policy.yaml` — no code changes required.

-----