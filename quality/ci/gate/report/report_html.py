"""
HTML Report Builder

Builds the complete HTML report string from evaluated.json data.

Functions
---------
build_html_report(evaluated_doc, env) -> str
    Build the full HTML quality gate report document.
"""

from datetime import datetime, timezone

from quality.ci.gate.report.report_constants import CATEGORY_MAP, SEVERITY_COLORS


CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: #f5f6fa; color: #2c3e50; padding: 24px; font-size: 14px;
}
h1 { font-size: 1.6em; margin-bottom: 8px; }
h2 { font-size: 1.2em; margin: 24px 0 12px; padding-bottom: 6px;
     border-bottom: 2px solid #dde; }
h3 { font-size: 1em; margin-bottom: 8px; color: #555; }
.banner { padding: 12px 20px; border-radius: 6px; color: #fff;
          font-weight: bold; font-size: 1.05em; margin: 16px 0; }
.info-card { background: #fff; border-radius: 6px; padding: 16px 20px;
             margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
.info-table td { padding: 4px 12px 4px 0; vertical-align: top; }
.info-table td:first-child { color: #7f8c8d; white-space: nowrap; }
table { width: 100%; border-collapse: collapse; background: #fff;
        border-radius: 6px; overflow: hidden;
        box-shadow: 0 1px 3px rgba(0,0,0,0.08); margin-bottom: 16px; }
th { background: #2c3e50; color: #fff; padding: 10px 14px; text-align: left;
     font-size: 0.85em; text-transform: uppercase; letter-spacing: 0.05em; }
td { padding: 8px 14px; border-bottom: 1px solid #eee; vertical-align: top; }
tr:last-child td { border-bottom: none; }
tr:hover td { background: #f8f9fa; }
code { background: #f0f2f5; padding: 2px 6px; border-radius: 3px;
       font-size: 0.9em; font-family: "SFMono-Regular", Consolas, monospace; }
.tool-section { background: #fff; border-radius: 6px; margin-bottom: 20px;
                box-shadow: 0 1px 3px rgba(0,0,0,0.08); overflow: hidden; }
.tool-header { padding: 14px 20px; background: #fafbfc;
               border-bottom: 1px solid #eee; }
.tool-title { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.tool-name { font-weight: bold; font-size: 1.05em;
             font-family: "SFMono-Regular", Consolas, monospace; }
.tool-category { color: #7f8c8d; font-size: 0.85em; }
.tool-meta { display: flex; align-items: center;
             margin-bottom: 8px; font-size: 0.9em; }
.sev-counts { margin-top: 6px; }
.tool-findings { padding: 16px 20px; }
.section-header { background: #2c3e50; color: #fff; padding: 10px 20px;
                  border-radius: 6px; margin: 24px 0 12px;
                  font-weight: bold; font-size: 1.05em; }
.section-header.advisory { background: #7f8c8d; }
footer { margin-top: 32px; padding-top: 12px;
         border-top: 1px solid #dde; color: #7f8c8d; font-size: 0.85em; }
a { color: #2980b9; text-decoration: none; }
a:hover { text-decoration: underline; }
a.tool-link { color: #2980b9; text-decoration: none;
              font-family: "SFMono-Regular", Consolas, monospace; }
a.tool-link:hover { text-decoration: underline; }
"""


def _severity_badge(severity: str | None) -> str:
    """Render an HTML severity badge."""
    if not severity:
        return "<span>&mdash;</span>"

    color = SEVERITY_COLORS.get(severity.lower(), "#95a5a6")
    return (
        f'<span style="background:{color};color:#fff;padding:2px 8px;'
        f'border-radius:4px;font-size:0.85em;font-weight:bold;">'
        f"{severity.upper()}</span>"
    )


def _finding_row(finding: dict) -> str:
    """Render a single finding row for the HTML findings table."""
    severity = finding.get("severity", "")
    message = str(finding.get("message", "—")).replace("<", "&lt;").replace(">", "&gt;")
    file_path = finding.get("file", "—")
    line = finding.get("line", "—")
    rule = finding.get("rule", "—")
    rule_url = finding.get("rule_url", "")

    rule_cell = f'<a href="{rule_url}" target="_blank">{rule}</a>' if rule_url else rule

    return (
        "<tr>"
        f"<td>{_severity_badge(severity)}</td>"
        f"<td><code>{file_path}</code></td>"
        f"<td>{line}</td>"
        f"<td>{rule_cell}</td>"
        f"<td>{message}</td>"
        "</tr>"
    )


def _sev_pills(sev_counts: dict) -> str:
    """Render severity count pills for a tool section."""
    pills = ""

    for level in ["critical", "high", "medium", "low", "info"]:
        count = sev_counts.get(level, 0)
        if count:
            color = SEVERITY_COLORS.get(level, "#95a5a6")
            pills += (
                f'<span style="background:{color};color:#fff;padding:2px 8px;'
                f'border-radius:4px;font-size:0.8em;margin-right:4px;">'
                f"{level.upper()}: {count}</span>"
            )

    return pills or '<span style="color:#7f8c8d;">No findings</span>'


def _tool_status(reason: str, violation: bool) -> tuple[str, str]:
    """Return status HTML and header color for a tool result."""
    if reason == "disabled":
        return '<span style="color:#7f8c8d;">DISABLED</span>', "#7f8c8d"
    if violation:
        return '<span style="color:#c0392b;">FAILURE</span>', "#c0392b"
    return '<span style="color:#27ae60;">SUCCESS</span>', "#27ae60"


def _tool_role(blocking: bool) -> str:
    """Return role HTML for a tool result."""
    return (
        '<span style="color:#c0392b;">Enforced</span>'
        if blocking
        else '<span style="color:#e67e22;">Advisory</span>'
    )


def _tool_reason_html(reason: str) -> str:
    """Return optional reason HTML for a tool result."""
    if not reason or reason == "disabled":
        return ""

    return (
        f'<span style="margin-left:12px;color:#7f8c8d;font-size:0.85em;">'
        f"Reason: <code>{reason}</code></span>"
    )


def _tool_findings_html(
    findings: list[dict],
    reason: str,
    normalized: dict,
) -> str:
    """Render the findings section for a tool result."""
    if findings:
        rows = "\n".join(_finding_row(finding) for finding in findings)
        return f"""
        <table>
            <thead><tr>
                <th>Severity</th><th>File</th><th>Line</th>
                <th>Rule</th><th>Message</th>
            </tr></thead>
            <tbody>{rows}</tbody>
        </table>"""

    if reason == "disabled":
        return "<p><em>Tool is disabled.</em></p>"

    if normalized.get("runtime_error", False):
        error_message = (normalized.get("metadata") or {}).get("error", "Unknown error")
        return f"<p><em>Runtime error: {error_message}</em></p>"

    if not normalized.get("executed", False):
        return "<p><em>Tool did not execute.</em></p>"

    return "<p><em>No findings detected.</em></p>"


def _tool_section(result: dict) -> str:
    """Render a full tool detail section."""
    tool = result.get("tool", "unknown")
    category = CATEGORY_MAP.get(tool, "Analysis")
    violation = result.get("policy_violation", False)
    blocking = result.get("blocking", False)
    reason = result.get("reason", "")
    normalized = result.get("normalized", {})
    findings = normalized.get("findings", [])
    severity_counts = normalized.get("severity_counts", {})

    status_html, header_color = _tool_status(reason, violation)
    role_html = _tool_role(blocking)
    reason_html = _tool_reason_html(reason)
    findings_html = _tool_findings_html(findings, reason, normalized)

    return f"""
    <div class="tool-section" id="tool-{tool}">
        <div class="tool-header" style="border-left:4px solid {header_color};">
            <div class="tool-title">
                <span class="tool-name">{tool}</span>
                <span class="tool-category">{category}</span>
            </div>
            <div class="tool-meta">
                {status_html}
                <span style="margin-left:12px;">Role: {role_html}</span>
                {reason_html}
            </div>
            <div class="sev-counts">{_sev_pills(severity_counts)}</div>
        </div>
        <div class="tool-findings">{findings_html}</div>
    </div>"""


def _summary_row(result: dict) -> str:
    """Render a summary table row for a tool."""
    tool = result.get("tool", "unknown")
    category = CATEGORY_MAP.get(tool, "Analysis")
    violation = result.get("policy_violation", False)
    blocking = result.get("blocking", False)
    reason = result.get("reason", "")
    normalized = result.get("normalized", {})
    finding_count = normalized.get("violation_count", 0)

    status_cell, _ = _tool_status(reason, violation)
    role_cell = _tool_role(blocking)
    findings_cell = str(finding_count) if finding_count else "&mdash;"

    return (
        "<tr>"
        f'<td><a class="tool-link" href="#tool-{tool}"><code>{tool}</code></a></td>'
        f"<td>{category}</td>"
        f"<td>{status_cell}</td>"
        f"<td>{role_cell}</td>"
        f"<td>{findings_cell}</td>"
        "</tr>\n"
    )


def _pr_block(env: dict) -> str:
    """Render the pull request metadata block when applicable."""
    if env.get("event_name") != "pull_request" or not env.get("pr_number"):
        return ""

    return f"""
    <div class="info-card">
        <h3>Pull Request</h3>
        <table class="info-table">
            <tr><td><strong>PR Number</strong></td><td>#{env['pr_number']}</td></tr>
            <tr><td><strong>PR Author</strong></td><td>@{env['actor']}</td></tr>
            <tr><td><strong>Source Branch</strong></td>
                <td><code>{env['head_ref']}</code></td></tr>
            <tr><td><strong>Target Branch</strong></td>
                <td><code>{env['base_ref']}</code></td></tr>
        </table>
    </div>"""


LEGEND_BLOCK = """
    <div class="info-card">
        <h3>Legend</h3>
        <table class="info-table">
            <tr><td>SUCCESS</td>
                <td>Tool ran and found no violations</td></tr>
            <tr><td>FAILURE</td>
                <td>Tool found one or more violations</td></tr>
            <tr><td>DISABLED</td>
                <td>Tool is not yet configured</td></tr>
            <tr><td>Enforced</td>
                <td>Violations from this tool will block the merge</td></tr>
            <tr><td>Advisory</td>
                <td>Violations are reported but will not block the merge</td></tr>
        </table>
    </div>"""

# ----------------------------------------------------------
# Report assembly helpers
# ----------------------------------------------------------

def _build_banner(overall_block: bool) -> tuple[str, str]:
    """
    Build the banner color and text shown at the top of the report.

    Parameters
    ----------
    overall_block : bool
        True when one or more enforced tools failed policy.

    Returns
    -------
    tuple[str, str]
        Banner color and banner message.
    """
    banner_color = "#c0392b" if overall_block else "#27ae60"
    banner_text = (
        "BLOCKED — One or more required checks failed. "
        "Fix the issues below before merging."
        if overall_block
        else "APPROVED — All required checks passed."
    )
    return banner_color, banner_text


def _build_run_metadata(env: dict) -> tuple[str, str, str]:
    """
    Build run-specific metadata used in the report header.

    Parameters
    ----------
    env : dict
        Environment metadata collected by report.py.

    Returns
    -------
    tuple[str, str, str]
        Short SHA, run URL, and commit URL.
    """
    sha_short = env["sha"][:7] if env.get("sha") else "unknown"
    run_url = f"{env['server_url']}/{env['repository']}/actions/runs/{env['run_id']}"
    commit_url = f"{env['server_url']}/{env['repository']}/commit/{env['sha']}"
    return sha_short, run_url, commit_url


def _build_summary_rows(all_results: list[dict]) -> str:
    """
    Render the summary table rows for all evaluated tools.

    Parameters
    ----------
    all_results : list[dict]
        Combined blocking and non-blocking evaluated results.

    Returns
    -------
    str
        HTML table rows for the summary table.
    """
    return "".join(_summary_row(result) for result in all_results)


def _build_tool_sections(
    blocking_results: list[dict],
    non_blocking_results: list[dict],
) -> tuple[str, str]:
    """
    Render the enforced and advisory tool sections.

    Parameters
    ----------
    blocking_results : list[dict]
        Evaluated results for enforced tools.
    non_blocking_results : list[dict]
        Evaluated results for advisory tools.

    Returns
    -------
    tuple[str, str]
        HTML for enforced-tool sections and advisory-tool sections.
    """
    blocking_sections = (
        "\n".join(_tool_section(result) for result in blocking_results)
        or "<p><em>No enforced tools configured.</em></p>"
    )

    non_blocking_sections = (
        "\n".join(_tool_section(result) for result in non_blocking_results)
        or "<p><em>No advisory tools configured.</em></p>"
    )

    return blocking_sections, non_blocking_sections


# ----------------------------------------------------------
# Main report builder
# ----------------------------------------------------------

def build_html_report(evaluated_doc: dict, env: dict) -> str:
    """
    Build the complete HTML report from evaluated results.

    Parameters
    ----------
    evaluated_doc : dict
        Evaluated quality gate document.
    env : dict
        Environment metadata for report rendering.

    Returns
    -------
    str
        Full HTML report string.
    """
    # ------------------------------------------------------
    # Extract evaluated result groups
    # ------------------------------------------------------
    overall_block = bool(evaluated_doc.get("overall_block", True))
    blocking_results = evaluated_doc.get("blocking_results", [])
    non_blocking_results = evaluated_doc.get("non_blocking_results", [])
    all_results = blocking_results + non_blocking_results

    # ------------------------------------------------------
    # Build reusable display values
    # ------------------------------------------------------
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    banner_color, banner_text = _build_banner(overall_block)
    sha_short, run_url, commit_url = _build_run_metadata(env)
    summary_rows = _build_summary_rows(all_results)
    blocking_sections, non_blocking_sections = _build_tool_sections(
        blocking_results,
        non_blocking_results,
    )

    # ------------------------------------------------------
    # Render final HTML document
    # ------------------------------------------------------
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CareConnect Quality Gate Report</title>
    <style>{CSS}</style>
</head>
<body>

<h1>CareConnect Quality Gate Report</h1>
<div class="banner" style="background:{banner_color};">{banner_text}</div>

<div class="info-card">
    <h3>Report Header</h3>
    <table class="info-table">
        <tr><td><strong>Generated (UTC)</strong></td><td>{generated_at}</td></tr>
        <tr><td><strong>Pipeline Run</strong></td>
            <td><a href="{run_url}" target="_blank">#{env['run_number']}</a></td></tr>
        <tr><td><strong>Trigger</strong></td>
            <td><code>{env['event_name']}</code></td></tr>
        <tr><td><strong>Scan Root</strong></td>
            <td><code>{env['scan_root']}</code></td></tr>
        <tr><td><strong>Commit SHA</strong></td>
            <td><a href="{commit_url}" target="_blank">
                <code>{sha_short}</code></a></td></tr>
    </table>
</div>

{_pr_block(env)}

{LEGEND_BLOCK}

<h2>Tool Results Summary</h2>
<table>
    <thead>
        <tr>
            <th>Tool</th><th>Category</th><th>Status</th>
            <th>Role</th><th>Findings</th>
        </tr>
    </thead>
    <tbody>{summary_rows}</tbody>
</table>

<div class="section-header">Enforced Tools</div>
{blocking_sections}

<div class="section-header advisory">Advisory Tools</div>
{non_blocking_sections}

<footer>
    Generated by CareConnect Quality Gate Engine &mdash; {generated_at}
</footer>

</body>
</html>"""
