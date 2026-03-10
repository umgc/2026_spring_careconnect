# CareConnect — Team D
**2026 Spring Cohort | UMGC**

> **Branch:** `team_d`
> **Proving Ground:** `team_d-james-cicd-gating`
> **Target:** `main` (after Team D approval)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Team D Scope](#2-team-d-scope)
3. [BD1 — Static Analysis Tooling](#3-bd1--static-analysis-tooling)
4. [BD2 — CI/CD Quality Gates](#4-bd2--cicd-quality-gates)
5. [Required GitHub Secrets](#5-required-github-secrets)
6. [Running Analysis Locally](#6-running-analysis-locally)
7. [Exclusion Documentation](#7-exclusion-documentation)
8. [BD3 — Native Payment Systems](#8-bd3--native-payment-systems)
9. [BD4 — Centralized Notifications](#9-bd4--centralized-notifications)
10. [Reproducing Reports Locally](#10-reproducing-reports-locally)
11. [Future Cohort Handoff Notes](#11-future-cohort-handoff-notes)

---

## 1. Project Overview

CareConnect is a healthcare application built with a **Flutter/Dart** frontend and a **Java Spring Boot** backend deployed on AWS. The application handles sensitive patient data and must comply with HIPAA requirements.

This branch (`team_d`) represents the Team D integration branch for the 2026 Spring semester. All Team D feature branches are developed and tested here before being promoted to `main` via pull request and team approval.

**Tech Stack:**
- Frontend: Flutter / Dart (`frontend/`)
- Backend: Java 17 / Spring Boot / Maven (`backend/core/`)
- Infrastructure: AWS via Terraform (`terraform_aws/`)
- CI/CD: GitHub Actions (`.github/workflows/`)

---

## 2. Team D Scope

Team D is responsible for the following business requirements this semester:

| ID | Requirement | Status |
|----|-------------|--------|
| BD1 | Static Analysis Tooling and Critical Finding Remediation | ✅ In Progress |
| BD2 | DevOps Enforcement — Block Builds/Deployments on Failures | ✅ In Progress |
| BD3 | Migrate Payments to Google and Apple Native Payment Systems | ✅ In Progress |
| BD4 | Centralized Notifications via AWS SNS and SES | ✅ In Progress |

BD3 and BD4 are included in this README for project continuity and future cohort reference. See Sections 8 and 9.

---

## 3. BD1 — Static Analysis Tooling

### Problem
The repository lacked enforced static analysis tooling, allowing critical bugs, insecure coding patterns, and maintainability issues to go undetected before merge.

### Solution
A multi-layered static analysis strategy has been implemented across local development and CI/CD:

### Where Each Tool Runs

| Layer | Tool | Language | Purpose |
|-------|------|----------|---------|
| Local (developer machine) | `flutter analyze` | Dart | Built-in linter using `analysis_options.yaml` |
| CI — Secrets Scan | TruffleHog | All | Detects committed secrets, API keys, credentials |
| CI — SAST | Checkstyle | Java | Enforces coding standards and style rules |
| CI — SAST | SpotBugs | Java | Bytecode analysis for bug patterns |
| CI — SAST | PMD | Java | Source analysis — unused vars, complexity, empty catches |
| CI — SAST | SonarQube | Java | Central quality hub and quality gate enforcement |
| CI — SAST | Semgrep | Java + Dart | OWASP Top 10, secrets, cross-language SAST |
| CI — SCA | OWASP Dependency-Check | Java (Maven) | CVE scanning for all Maven dependencies |
| Post-Deploy — DAST | OWASP ZAP | Runtime | Active scanning against deployed AWS environment |

### Tool Configuration Files

| Tool | Config Location |
|------|----------------|
| flutter analyze | `frontend/analysis_options.yaml` |
| Checkstyle | `backend/core/src/main/resources/checkstyle.xml` (or `pom.xml`) |
| SpotBugs | `backend/core/pom.xml` — `<excludeFilterFile>` |
| PMD | `backend/core/pom.xml` — `<rulesets>` |
| SonarQube | `backend/core/pom.xml` — `sonar.projectKey` property |
| Semgrep | `.semgrep/` directory (custom rules, auto-discovered) |
| OWASP ZAP | `zap-rules.tsv` (suppression file, repo root) |

### Severity Policy

| Severity | CVSS Score | Action |
|----------|------------|--------|
| Critical | 9.0 – 10.0 | ❌ Hard block — merge prevented |
| High | 7.0 – 8.9 | ❌ Hard block — merge prevented |
| Medium | 4.0 – 6.9 | ⚠️ Warning — reported, merge allowed |
| Low | 0.0 – 3.9 | ⚠️ Warning — reported, merge allowed |

---

## 4. BD2 — CI/CD Quality Gates

### Problem
Builds and deployments were proceeding even when tests failed or security scans identified serious issues, creating risk of releasing unstable or insecure software into a healthcare environment.

### Solution
GitHub Actions workflow (`.github/workflows/build-and-analyze.yml`) enforces quality gates on every push and pull request to `main`, `develop`, and `team_d`.

### Workflow Jobs

```
push / pull_request
        │
        ├── secrets-scan      (TruffleHog)
        ├── sast-java         (Checkstyle, SpotBugs, PMD, SonarQube)
        ├── sast-flutter      (Semgrep, flutter analyze)
        └── sca               (OWASP Dependency-Check)
                │
                └── report    (aggregates all results → PR comment + artifact)
```

OWASP ZAP (DAST) runs separately as a manual trigger post-deployment.

### How the Gate Works

1. All four analysis jobs run **in parallel**
2. The `report` job waits for all of them (regardless of outcome)
3. It generates a unified Markdown + JSON report
4. The report is **posted as a comment on the PR** (updates on re-run, no spam)
5. The report is **uploaded as a downloadable artifact** (90-day retention for HIPAA audit trail)
6. If any blocking tool failed, the `report` job **fails the workflow**
7. GitHub branch protection rules prevent merge when the workflow fails

### Enabling Branch Protection

To enforce gate blocking on `main` and `team_d`:

1. Go to GitHub → Repository → **Settings → Branches**
2. Add a rule for `main` (and optionally `team_d`)
3. Check **"Require status checks to pass before merging"**
4. Add **"Generate Report & Enforce Gates"** as a required check
5. Check **"Require branches to be up to date before merging"**

### Disabling a Tool Temporarily

Each tool has a toggle flag at the top of the workflow file:

```yaml
env:
  RUN_TRUFFLEHOG: 'true'
  RUN_CHECKSTYLE: 'true'
  RUN_SPOTBUGS: 'true'
  RUN_PMD: 'true'
  RUN_SONARQUBE: 'true'
  RUN_SEMGREP: 'true'
  RUN_FLUTTER_ANALYZE: 'true'
  RUN_OWASP_DC: 'true'
```

Set any value to `'false'` to skip that tool. The job still passes; only that step is skipped.

### Understanding the Report

The PR comment report contains:

- Report timestamp and pipeline run link
- PR number, title, author, source and target branch
- Commit SHA, message, code author, and push timestamp
- Per-tool pass/fail summary table
- Detailed findings for each failed tool (file path, line number, rule, severity, rule reference URL)
- OWASP CVE warnings (non-blocking Medium/Low findings)
- Final merge status: APPROVED or BLOCKED

The downloadable artifact bundle (Actions → Run → Artifacts → `analysis-report-bundle`) contains the full Markdown report, JSON report, and all raw XML tool outputs.

---

## 5. Required GitHub Secrets

Go to: **GitHub → Repository → Settings → Secrets and Variables → Actions**

| Secret | Required | Purpose | How to Get It |
|--------|----------|---------|---------------|
| `NVD_API_KEY` | Recommended | OWASP Dependency-Check NVD database access. Without it, scanning is rate-limited and very slow. | Free key at [nvd.nist.gov/developers/request-an-api-key](https://nvd.nist.gov/developers/request-an-api-key) |
| `SONAR_HOST_URL` | Optional | Your SonarQube server URL | Your SonarQube instance URL, e.g. `https://sonar.yourcompany.com` |
| `SONAR_TOKEN` | Optional | SonarQube authentication | Generated in SonarQube → My Account → Security → Generate Token |
| `STAGING_URL` | DAST only | AWS staging environment URL for OWASP ZAP to scan | Run `cd terraform_aws/4_compute && terraform output alb_dns_name` |

**Notes:**
- If `SONAR_HOST_URL` or `SONAR_TOKEN` are missing, SonarQube is skipped with a warning. The pipeline does not hard-block. To make it required, see the comments in the workflow file.
- If `NVD_API_KEY` is missing, OWASP scanning still runs but will be significantly slower due to NVD rate limiting.
- `STAGING_URL` is only used by the DAST job, which runs manually (`workflow_dispatch`) and never on push/PR.

---

## 6. Running Analysis Locally

### Prerequisites

- Java 17 (Temurin recommended)
- Maven (or use `./mvnw` wrapper in `backend/core/`)
- Flutter SDK (stable channel)
- Dart SDK (included with Flutter)

### Flutter Analyze (Dart/Flutter — run before every push)

```bash
cd frontend
flutter pub get
flutter analyze
dart format --set-exit-if-changed .
```

`flutter analyze` uses the rules defined in `frontend/analysis_options.yaml`. Fix all errors before pushing — this is your first line of defense.

### Checkstyle (Java)

```bash
cd backend/core
./mvnw checkstyle:check
```

Results: `target/checkstyle-result.xml`

### SpotBugs (Java)

```bash
cd backend/core
./mvnw spotbugs:check
```

Results: `target/spotbugsXml.xml`

### PMD (Java)

```bash
cd backend/core
./mvnw pmd:check pmd:cpd-check
```

Results: `target/pmd.xml`, `target/cpd.xml`

### OWASP Dependency-Check (Java)

```bash
cd backend/core
./mvnw org.owasp:dependency-check-maven:check \
  -Dformat=ALL \
  -DfailBuildOnCVSS=7 \
  -Dnvd.api.key=YOUR_NVD_API_KEY
```

Results: `target/dependency-check-report.html` (open in browser for full CVE details)

### Run All Java Tools in One Command

```bash
cd backend/core
./mvnw checkstyle:check spotbugs:check pmd:check pmd:cpd-check verify
```

---

## 7. Exclusion Documentation

Per **BD1**, when critical or high vulnerabilities cannot be fixed immediately, the following must be documented: the exclusion rationale, potential impact, and recommended remediation steps. This ensures transparency for future cohorts.

> **Current Status:** No exclusions at time of initial implementation.
> As exclusions are identified during remediation, they will be added to the table below.

| Finding ID | Tool | Severity | File / Dependency | Rule / CVE | Rationale for Exclusion | Potential Impact | Recommended Remediation | Date Documented | Documented By |
|------------|------|----------|-------------------|------------|--------------------------|------------------|-------------------------|-----------------|---------------|
| _(none yet)_ | — | — | — | — | — | — | — | — | — |

**To add an exclusion:** Open a PR adding a row to the table above with all fields completed. The PR description must include team lead approval.

---

## 8. BD3 — Native Payment Systems

**Out of Team D scope for Spring 2026.**

### Problem
The application currently uses Stripe for payments, which may conflict with Apple App Store and Google Play Store policies depending on payment use cases, creating compliance risk and potential app rejection.

### Business Need
Replace Stripe-based payments with native Google Pay and Apple Pay to ensure platform policy compliance, reduce app store rejection risk, and provide a seamless payment experience for mobile users.

### Current State
Stripe integration exists in the frontend codebase. No migration work has been started.

### Recommended Approach for Future Cohort
- **Android:** Integrate Google Pay API via the `pay` Flutter package
- **iOS:** Integrate Apple Pay via the same `pay` Flutter package (unified API)
- **Backend:** Update payment processing endpoints in Spring Boot to handle tokenized payment data from native payment systems instead of Stripe payment intents
- **Testing:** Both platforms require sandbox/test environments before production submission
- **Reference:** [pub.dev/packages/pay](https://pub.dev/packages/pay)

---

## 9. BD4 — Centralized Notifications via AWS SNS and SES

**Out of Team D scope for Spring 2026.**

### Problem
Notifications and reminders are currently implemented as hardcoded placeholders with no centralized delivery mechanism, making them unreliable and unscalable.

### Business Need
Implement a centralized notification architecture using **AWS SNS** (push notifications) and **AWS SES** (email delivery) so reminders can be reliably sent, configured, and scaled across channels.

### Current State
Notification logic exists as placeholder code. No AWS SNS or SES infrastructure is provisioned in Terraform.

### Recommended Approach for Future Cohort
- **Terraform:** Add SNS topic and SES configuration to `terraform_aws/` (likely a new `5_notifications` layer following existing conventions)
- **Backend:** Create a `NotificationService` in Spring Boot that publishes to SNS topics
- **Email:** Configure SES with verified domain, templates for appointment reminders, and bounce/complaint handling
- **Flutter:** Subscribe to SNS push topics via AWS Amplify or direct FCM/APNS integration
- **Reference:** [AWS SNS Developer Guide](https://docs.aws.amazon.com/sns/latest/dg/welcome.html) | [AWS SES Developer Guide](https://docs.aws.amazon.com/ses/latest/dg/Welcome.html)

---

## 10. Reproducing Reports Locally

Per **BD4**, report generation must be reproducible locally. Follow these steps to generate the same reports the CI pipeline produces, on your local machine.

### Step 1 — Run all Java analysis tools

```bash
cd backend/core
./mvnw clean verify checkstyle:check spotbugs:check pmd:check pmd:cpd-check \
  org.owasp:dependency-check-maven:check \
  -Dformat=ALL \
  -DfailBuildOnCVSS=7 \
  -Dnvd.api.key=YOUR_NVD_API_KEY
```

### Step 2 — Run Flutter analysis

```bash
cd frontend
flutter pub get
flutter analyze --machine > flutter-analyze-output.json
```

### Step 3 — Run Semgrep locally

```bash
# Install Semgrep
pip install semgrep

# Run against the full repo from repo root
semgrep --config p/default --config p/owasp-top-ten --config p/secrets \
  --sarif --output semgrep-local.sarif .
```

### Step 4 — Find your reports

| Tool | Report Location |
|------|----------------|
| Checkstyle | `backend/core/target/checkstyle-result.xml` |
| SpotBugs | `backend/core/target/spotbugsXml.xml` |
| PMD | `backend/core/target/pmd.xml` |
| OWASP Dependency-Check | `backend/core/target/dependency-check-report.html` |
| Flutter Analyze | `frontend/flutter-analyze-output.json` |
| Semgrep | `semgrep-local.sarif` |
| CI Unified Report | Download from GitHub Actions → Run → Artifacts → `analysis-report-bundle` |

---

## 11. Future Cohort Handoff Notes

### What Was Delivered (Team D, Spring 2026)

- Full GitHub Actions workflow implementing TruffleHog, Checkstyle, SpotBugs, PMD, SonarQube, Semgrep, flutter analyze, and OWASP Dependency-Check
- Parallel job architecture with unified report aggregation
- PR comment reporting (auto-updates on re-run)
- 90-day artifact retention for HIPAA audit trail
- Tool toggle flags for easy enable/disable per tool
- OWASP ZAP scaffolded for post-deployment DAST (manual trigger)
- Test branch suite for validating pipeline behavior

### What Still Needs to Be Done

- **SonarQube:** Needs a hosted SonarQube instance configured and secrets added. Project key must be set in `pom.xml` under `sonar.projectKey`. Currently skips with a warning if not configured.
- **OWASP ZAP:** Needs `STAGING_URL` secret populated with the actual AWS ALB DNS name from Terraform `4_compute` output. Currently scaffolded with a placeholder.
- **Critical/High Finding Remediation:** Run the pipeline against the full codebase and work through all blocking findings. Document any that cannot be fixed in Section 7 of this README.
- **BD3:** Native payment migration (see Section 8)
- **BD4:** AWS SNS/SES notification infrastructure (see Section 9)
- **Branch Protection:** Enable required status checks on `main` in GitHub Settings (see Section 4)

### Known Limitations

- TruffleHog uses `--only-verified` which means fake/inactive secrets are detected but do not hard-fail. This is intentional to reduce false positives. Remove `--only-verified` from the workflow if you want detection-only mode to also block.
- OWASP Dependency-Check without an `NVD_API_KEY` secret will run but slowly due to NVD rate limiting.
- SonarQube quality gate enforcement requires a running SonarQube server — it is currently advisory only.

### Branch Structure Convention

```
your-feature-branch
        ↓ PR
     team_d          ← team integration + CI proving ground
        ↓ PR (after team approval)
      main           ← production
```

Never push directly to `main`. All changes flow through `team_d` first.

### Repository Structure Reference

```
2026_spring_careconnect/
├── .github/workflows/
│   └── build-and-analyze.yml   ← CI/CD workflow (Team D)
├── backend/core/               ← Java Spring Boot
├── frontend/                   ← Flutter/Dart
├── terraform_aws/              ← AWS infrastructure
│   ├── 4_compute/              ← ALB DNS output used by OWASP ZAP
│   └── ...
└── docs/                       ← Project documentation
```

---

*README maintained by Team D — 2026 Spring Cohort | UMGC*
*Last updated: February 2026*
