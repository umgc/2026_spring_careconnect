"""
Markdown Report Builder

Builds the markdown report string consumed by:

- GitHub Actions job summary
- Pull request comment

Functions
---------
build_markdown_report(evaluated_doc, env) -> str
    Build the complete markdown quality gate report.
"""

from datetime import datetime, timezone

from quality.ci.gate.report.report_constants import (
    CATEGORY_MAP,
    _MD_TABLE_HEADER,
    _MD_TABLE_SEPARATOR,
)


PR_COMMENT_MARKER = "## CareConnect — Security & Quality Analysis Report"


def _summary_row(result: dict) -> str:
    """Build one markdown summary row for a tool result."""
    tool = result.get("tool", "unknown")
    category = CATEGORY_MAP.get(tool, "Analysis")
    violation = result.get("policy_violation", False)
    blocking = result.get("blocking", False)
    reason = result.get("reason", "")
    normalized = result.get("normalized", {})
    finding_count = normalized.get("violation_count", 0)
    findings_label = f"{finding_count} finding(s)" if finding_count else "—"

    if reason == "disabled":
        status = "DISABLED"
    elif violation:
        status = "FAILURE"
    else:
        status = "SUCCESS"

    role = "Enforced" if blocking else "Advisory"
    return f"| {tool} | {category} | {status} | {role} | {findings_label} |"


def build_markdown_report(evaluated_doc: dict, env: dict) -> str:
    """
    Build the markdown quality gate report.

    Parameters
    ----------
    evaluated_doc : dict
        Evaluated quality gate document.
    env : dict
        Environment metadata used for report rendering.

    Returns
    -------
    str
        Complete markdown report body.
    """
    report_data = {
        "overall_block": bool(evaluated_doc.get("overall_block", True)),
        "blocking_results": evaluated_doc.get("blocking_results", []),
        "non_blocking_results": evaluated_doc.get("non_blocking_results", []),
    }
    report_data["all_results"] = (
        report_data["blocking_results"] + report_data["non_blocking_results"]
    )

    render_data = {
        "sha_short": env["sha"][:7] if env["sha"] else "unknown",
        "run_url": f"{env['server_url']}/{env['repository']}/actions/runs/{env['run_id']}",
        "commit_url": f"{env['server_url']}/{env['repository']}/commit/{env['sha']}",
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "approval_line": (
            "> **BLOCKED** — One or more required checks failed. "
            "Fix the issues below before merging."
            if report_data["overall_block"]
            else "> **APPROVED** — All required checks passed."
        ),
    }

    lines: list[str] = [
        "# CareConnect Quality Gate Report",
        "",
        render_data["approval_line"],
        "",
        PR_COMMENT_MARKER,
        "",
        "### Report Header",
        "",
        _MD_TABLE_HEADER,
        _MD_TABLE_SEPARATOR,
        f"| **Generated (UTC)** | {render_data['generated_at']} |",
        f"| **Pipeline Run** | [#{env['run_number']}]({render_data['run_url']}) |",
        f"| **Trigger** | `{env['event_name']}` |",
        f"| **Scan Root** | `{env['scan_root']}` |",
        "",
        "_All timestamps are reported in Coordinated Universal Time (UTC)._",
        "",
    ]

    if env["event_name"] == "pull_request" and env["pr_number"]:
        lines += [
            "### Pull Request",
            "",
            _MD_TABLE_HEADER,
            _MD_TABLE_SEPARATOR,
            f"| **PR Number** | #{env['pr_number']} |",
            f"| **PR Author** | @{env['actor']} |",
            f"| **Source Branch** | `{env['head_ref']}` |",
            f"| **Target Branch** | `{env['base_ref']}` |",
            "",
        ]

    lines += [
        "### Commit Details",
        "",
        _MD_TABLE_HEADER,
        _MD_TABLE_SEPARATOR,
        f"| **Commit SHA** | `{render_data['sha_short']}` ([full]({render_data['commit_url']})) |",
        "",
        "### Legend",
        "",
        "| Status | Meaning |",
        "|--------|---------|",
        "| SUCCESS | Tool ran and found no violations |",
        "| FAILURE | Tool found one or more violations |",
        "| DISABLED | Tool is not yet configured |",
        "| Enforced | Violations from this tool will block the merge |",
        "| Advisory | Violations are reported but will not block the merge |",
        "",
        "### Tool Results Summary",
        "",
        "| Tool | Category | Status | Role | Findings |",
        "|------|----------|--------|------|----------|",
    ]

    lines.extend(_summary_row(result) for result in report_data["all_results"])

    lines += [
        "",
        "---",
        "_Full artifact bundle available in the workflow run artifacts._",
        "",
    ]

    return "\n".join(lines)
