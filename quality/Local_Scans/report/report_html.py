"""
Report HTML Builder

Builds the complete HTML report string from parsed findings.
Matches the CI report style from quality/ci/gate/report.py.

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
.tool-meta { margin-bottom: 8px; font-size: 0.9em; }
.sev-counts { margin-top: 6px; }
.tool-findings { padding: 16px 20px; }
footer { margin-top: 32px; padding-top: 12px;
         border-top: 1px solid #dde; color: #7f8c8d; font-size: 0.85em; }
"""

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


# ----------------------------------------------------------
# Findings and section builders
# ----------------------------------------------------------

def _finding_rows(findings: list) -> str:
    """Render HTML table rows for all findings in a tool section."""
    rows = ""

    for finding in findings:
        rows += (
            "<tr>"
            f"<td>{_severity_badge(finding.get('severity'))}</td>"
            f"<td><code>{finding.get('file', '')}</code></td>"
            f"<td>{finding.get('line', '')}</td>"
            f"<td>{finding.get('rule', '')}</td>"
            f"<td>{finding.get('message', '')}</td>"
            "</tr>"
        )

    if not rows:
        rows = '<tr><td colspan="5" style="color:#7f8c8d;">No findings</td></tr>'

    return rows


def _tool_section(
    tool_name: str,
    status: str,
    findings: list,
    severity_counts: dict,
) -> str:
    """
    Build one tool section including status, severity summary,
    and findings table.
    """
    return f"""
<div class="tool-section" style="border-left:6px solid {_border_color(status)};">
<div class="tool-header">
<div class="tool-title">
<span class="tool-name">{tool_name}</span>
</div>
<div class="tool-meta">{_status_html(status)}</div>
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
</div>
"""


def _build_sections(context: dict) -> str:
    """Render all tool sections for the local report."""
    sections = [
        _tool_section(
            "Flutter Analyze",
            context["fl_status"],
            context["fl_findings"],
            context["fl_sev"],
        ),
        _tool_section(
            "Checkstyle",
            context["cs_status"],
            context["cs_findings"],
            context["cs_sev"],
        ),
        _tool_section(
            "PMD",
            context["pmd_status"],
            context["pmd_findings"],
            context["pmd_sev"],
        ),
        _tool_section(
            "SpotBugs",
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
        "BLOCKED — One or more required checks failed."
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
    sections_html = _build_sections(context)

    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>CareConnect Local Quality Gate</title>
<style>{CSS}</style>
</head>

<body>

<h1>CareConnect Local Quality Gate Report</h1>

<div class="banner" style="background:{banner_color};">
{banner_text}
</div>

<div class="info-card">
<table class="info-table">
<tr><td><strong>Generated</strong></td><td>{context["generated_at"]}</td></tr>
<tr><td><strong>User</strong></td><td>{context["scan_user"]}</td></tr>
<tr><td><strong>Repository</strong></td><td><code>{context["repo_root"]}</code></td></tr>
</table>
</div>

<h2>Tool Results</h2>

{sections_html}

<footer>
Generated by CareConnect Local Quality Gate — {context["generated_at"]}
</footer>

</body>
</html>
"""
