"""
Report HTML Builder

Builds the complete HTML report string from parsed findings.
Matches the CI report style from quality/ci/gate/report/report_html.py.

Functions:
  build_html(context) -> str

context dict keys:
  generated_at  — UTC timestamp string
  scan_user     — local username
  repo_root     — repository root path
  failed        — number of failed tools
  fl_status     — passed | failed | skipped
  cs_status     — passed | failed | skipped
  pmd_status    — passed | failed | skipped
  sb_status     — passed | failed | skipped
  fl_findings   — list of finding dicts
  cs_findings   — list of finding dicts
  pmd_findings  — list of finding dicts
  sb_findings   — list of finding dicts
  fl_sev        — severity count dict
  cs_sev        — severity count dict
  pmd_sev       — severity count dict
  sb_sev        — severity count dict
"""

# ----------------------------------------------------------
# Constants
# ----------------------------------------------------------

SEVERITY_COLORS = {
    "critical": "#7c0000",
    "high": "#c0392b",
    "medium": "#e67e22",
    "low": "#f1c40f",
    "info": "#3498db",
}

CATEGORY_MAP = {
    "Flutter Analyze": "SAST — Flutter",
    "Checkstyle":      "SAST — Java",
    "PMD":             "SAST — Java",
    "SpotBugs":        "SAST — Java",
}

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
a.tool-link { color: #2980b9; text-decoration: none;
              font-family: "SFMono-Regular", Consolas, monospace; }
a.tool-link:hover { text-decoration: underline; }
footer { margin-top: 32px; padding-top: 12px;
         border-top: 1px solid #dde; color: #7f8c8d; font-size: 0.85em; }
"""

# ----------------------------------------------------------
# Legend block
# ----------------------------------------------------------

LEGEND_BLOCK = """
<div class="info-card">
    <h3>Legend</h3>
    <table class="info-table">
        <tr><td>PASSED</td>
            <td>Tool ran and found no violations</td></tr>
        <tr><td>FAILED</td>
            <td>Tool found one or more violations</td></tr>
        <tr><td>SKIPPED</td>
            <td>Tool did not run (project type not detected)</td></tr>
        <tr><td>Enforced</td>
            <td>Violations from this tool will block the merge</td></tr>
    </table>
</div>"""


# ----------------------------------------------------------
# Small UI helpers
# ----------------------------------------------------------

def _severity_badge(severity: str) -> str:
    """Render a severity badge."""
    severity = (severity or "info").lower()
    color = SEVERITY_COLORS.get(severity, "#95a5a6")
    return (
        f'<span style="background:{color};color:#fff;padding:2px 8px;'
        f'border-radius:4px;font-size:0.85em;font-weight:bold;">'
        f"{severity.upper()}</span>"
    )


def _status_html(status: str) -> str:
    """Render pass/fail/skipped status text."""
    if status == "passed":
        return '<span style="color:#27ae60;">PASSED</span>'
    if status == "failed":
        return '<span style="color:#c0392b;">FAILED</span>'
    return '<span style="color:#7f8c8d;">SKIPPED</span>'


def _border_color(status: str) -> str:
    """Return the left-border color for a tool section."""
    if status == "passed":
        return "#27ae60"
    if status == "failed":
        return "#c0392b"
    return "#7f8c8d"


def _sev_pills(counts: dict) -> str:
    """Render severity summary pills."""
    pills = ""

    for level in ["critical", "high", "medium", "low", "info"]:
        count = counts.get(level, 0)
        if count:
            color = SEVERITY_COLORS.get(level, "#95a5a6")
            pills += (
                f'<span style="background:{color};color:#fff;'
                f'padding:2px 8px;border-radius:4px;'
                f'font-size:0.8em;margin-right:4px;">'
                f"{level.upper()}: {count}</span>"
            )

    return pills or '<span style="color:#7f8c8d;">No findings</span>'


def _finding_count(findings: list) -> str:
    """Return finding count or em dash if zero."""
    return str(len(findings)) if findings else "&mdash;"


# ----------------------------------------------------------
# Summary table
# ----------------------------------------------------------

def _summary_row(
    tool_id: str,
    tool_name: str,
    status: str,
    findings: list,
) -> str:
    """Render one summary table row."""
    category = CATEGORY_MAP.get(tool_name, "Analysis")
    return (
        "<tr>"
        f'<td><a class="tool-link" href="#tool-{tool_id}">{tool_name}</a></td>'
        f"<td>{category}</td>"
        f"<td>{_status_html(status)}</td>"
        f'<td><span style="color:#c0392b;">Enforced</span></td>'
        f"<td>{_finding_count(findings)}</td>"
        "</tr>"
    )


def _build_summary_table(context: dict) -> str:
    """Render the full Tool Results Summary table."""
    rows = (
        _summary_row("flutter-analyze", "Flutter Analyze",
                     context["fl_status"], context["fl_findings"])
        + _summary_row("checkstyle", "Checkstyle",
                       context["cs_status"], context["cs_findings"])
        + _summary_row("pmd", "PMD",
                       context["pmd_status"], context["pmd_findings"])
        + _summary_row("spotbugs", "SpotBugs",
                       context["sb_status"], context["sb_findings"])
    )

    return f"""
<table>
    <thead>
        <tr>
            <th>Tool</th>
            <th>Category</th>
            <th>Status</th>
            <th>Role</th>
            <th>Findings</th>
        </tr>
    </thead>
    <tbody>
        {rows}
    </tbody>
</table>"""


# ----------------------------------------------------------
# Findings and section builders
# ----------------------------------------------------------

def _finding_rows(findings: list) -> str:
    """Render HTML table rows for all findings in a tool section."""
    rows = ""

    for finding in findings:
        message = str(finding.get("message", "")).replace("<", "&lt;").replace(">", "&gt;")
        rows += (
            "<tr>"
            f"<td>{_severity_badge(finding.get('severity'))}</td>"
            f"<td><code>{finding.get('file', '')}</code></td>"
            f"<td>{finding.get('line', '')}</td>"
            f"<td>{finding.get('rule', '')}</td>"
            f"<td>{message}</td>"
            "</tr>"
        )

    if not rows:
        rows = '<tr><td colspan="5" style="color:#7f8c8d;">No findings</td></tr>'

    return rows


def _tool_section(
    tool_id: str,
    tool_name: str,
    status: str,
    findings: list,
    severity_counts: dict,
) -> str:
    """
    Build one tool section including category, status, role,
    severity summary, and findings table.
    """
    category = CATEGORY_MAP.get(tool_name, "Analysis")

    return f"""
<div class="tool-section" id="tool-{tool_id}"
     style="border-left:4px solid {_border_color(status)};">
    <div class="tool-header">
        <div class="tool-title">
            <span class="tool-name">{tool_name}</span>
            <span class="tool-category">{category}</span>
        </div>
        <div class="tool-meta">
            {_status_html(status)}
            <span style="margin-left:12px;">
                Role: <span style="color:#c0392b;">Enforced</span>
            </span>
        </div>
        <div class="sev-counts">{_sev_pills(severity_counts)}</div>
    </div>
    <div class="tool-findings">
        <table>
            <thead>
                <tr>
                    <th>Severity</th>
                    <th>File</th>
                    <th>Line</th>
                    <th>Rule</th>
                    <th>Message</th>
                </tr>
            </thead>
            <tbody>
                {_finding_rows(findings)}
            </tbody>
        </table>
    </div>
</div>"""


def _build_sections(context: dict) -> str:
    """Render all tool sections for the local report."""
    sections = [
        _tool_section(
            "flutter-analyze", "Flutter Analyze",
            context["fl_status"],
            context["fl_findings"],
            context["fl_sev"],
        ),
        _tool_section(
            "checkstyle", "Checkstyle",
            context["cs_status"],
            context["cs_findings"],
            context["cs_sev"],
        ),
        _tool_section(
            "pmd", "PMD",
            context["pmd_status"],
            context["pmd_findings"],
            context["pmd_sev"],
        ),
        _tool_section(
            "spotbugs", "SpotBugs",
            context["sb_status"],
            context["sb_findings"],
            context["sb_sev"],
        ),
    ]
    return "\n".join(sections)


def _build_banner(context: dict) -> tuple[str, str]:
    """Build banner color and banner text."""
    banner_color = "#c0392b" if context["failed"] else "#27ae60"
    banner_text = (
        "BLOCKED — One or more required checks failed. "
        "Fix the issues below before merging."
        if context["failed"]
        else "APPROVED — All required checks passed."
    )
    return banner_color, banner_text


# ----------------------------------------------------------
# Main report builder
# ----------------------------------------------------------

def build_html(context: dict) -> str:
    """
    Build the complete local HTML report.

    Parameters
    ----------
    context : dict
        Report context produced by generate_report.py.

    Returns
    -------
    str
        Full HTML document.
    """
    banner_color, banner_text = _build_banner(context)
    summary_table = _build_summary_table(context)
    sections_html = _build_sections(context)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CareConnect Local Quality Gate Report</title>
    <style>{CSS}</style>
</head>
<body>

<h1>CareConnect Local Quality Gate Report</h1>

<div class="banner" style="background:{banner_color};">
    {banner_text}
</div>

<div class="info-card">
    <h3>Report Header</h3>
    <table class="info-table">
        <tr><td><strong>Generated (UTC)</strong></td>
            <td>{context["generated_at"]}</td></tr>
        <tr><td><strong>User</strong></td>
            <td>{context["scan_user"]}</td></tr>
        <tr><td><strong>Repository</strong></td>
            <td><code>{context["repo_root"]}</code></td></tr>
    </table>
</div>

{LEGEND_BLOCK}

<h2>Tool Results Summary</h2>
{summary_table}

<div class="section-header">Enforced Tools</div>
{sections_html}

<footer>
    Generated by CareConnect Local Quality Gate &mdash; {context["generated_at"]}
</footer>

</body>
</html>"""
