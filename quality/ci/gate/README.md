# CareConnect CI Quality Gate Engine

## Overview

This directory contains the CI Quality Gate Engine for CareConnect. The gate engine runs automatically on every pull request and push to `team_d` and `team_d-*` branches. It executes 13 static analysis, security, and vulnerability scanning tools, evaluates results against a centralized policy, and enforces merge decisions via GitHub Actions exit codes. No manual intervention is required to trigger the pipeline.

---

## Viewing Results

There are two ways to view gate engine results.

**Option 1 — GitHub Actions**
Navigate to the repository on GitHub and click the **Actions** tab. Select the most recent **Build and Analyze** workflow run. Once the workflow completes, click on the workflow run to open it. At the top of the page you will find the **CareConnect Quality Gate Report** — a summary of all tool results, policy decisions, and the overall merge status for that run.

**Option 2 — Pull Request Checks**
When a pull request is opened, the gate engine runs automatically as a required check. All passing policy checks must complete before a merge is permitted. The check status is displayed directly on the pull request page, and the full report is posted as a comment on the PR.

> **Note:** Branch protection enforcement is currently disabled while known violations across the codebase are being resolved. Once the codebase reaches a passing state, branch protection will be enabled and merges will be blocked until all required checks pass. This applies to both Actions runs and pull request checks.

---

## Download the Artifact Bundle

Scroll to the bottom of the workflow run page. Under **Artifacts**, click the download button to download the artifact bundle as a ZIP file. Extract the ZIP to view the full analysis output.

---

## Artifact Bundle Contents

The artifact bundle contains the following structure:

```
quality/analysis/
├── raw/
├── normalized/
├── evaluated/
├── report.md
└── report.html
```

**raw/**
Contains the native output from each scanning tool in its original format. These files are the unmodified evidence layer — the source of truth for every finding reported by the gate engine. If a tool flagged a violation, the raw file contains the full detail as emitted by that tool.

**normalized/**
Contains `normalized.json` — a single unified file produced by the normalization layer. All tool outputs, regardless of their native format (XML, JSON, or JSONL), have been converted into a consistent schema. This allows the policy engine to evaluate every tool result in the same way without tool-specific logic.

**evaluated/**
Contains `evaluated.json` — the output of the policy engine. This file records the policy decision for each tool: whether a violation occurred, whether the tool is blocking or advisory, the reason for the decision, and the overall merge outcome for the pipeline run.

**report.md**
The Markdown version of the quality gate report. This is the same report displayed on the GitHub Actions Job Summary page. It provides a concise overview of all tool results, violation counts, severity levels, and the final merge decision.

**report.html**
A fully self-contained, human-readable HTML report with all results organized by tool category. Each tool section includes finding details such as file, line number, severity, rule, and message. The report includes hyperlinked navigation between the summary and each tool's detail section, making it easy to triage violations without parsing raw files.

---

## Adding a New Tool

To add a new scanning tool to the pipeline:
1. Write a parser under `quality/ci/gate/parsers/`
2. Register it in `quality/ci/gate/normalize.py`
3. Add a policy entry in `quality/ci/gate/policy.yaml`
4. Add the tool's category to `quality/ci/gate/report/report_constants.py`
5. Add the workflow step to `.github/workflows/build-and-analyze.yml`

No changes to the gate engine, policy engine, or reporting layer are required.

---

## Policy Configuration

All enforcement rules are defined in `quality/ci/gate/policy.yaml`. This is the single file that controls which tools block merges, what thresholds trigger a violation, and whether a tool is active. Enforcement behavior can be changed by editing this file — no code changes are required.

---