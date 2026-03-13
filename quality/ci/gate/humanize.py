"""
Human-Readable Results Layer (Layer 3 of the Quality Gate Engine)

Purpose
-------
Create a human-friendly, Markdown-based view of tool findings that maps:

- Tool name and enforcement status
- Actual error or message per finding
- File path and line number, when available
- Contextual code snippet from the repository checkout

Inputs
------
quality/analysis/normalized/normalized.json
quality/analysis/evaluated/evaluated.json

Outputs
-------
quality/analysis/human/index.md
quality/analysis/human/<tool>.md

Design Rules
------------
- Does not change policy outcomes. This layer is read-only.
- Must be resilient. Failures here must never break enforcement.
- If file or line is missing, the finding message is still rendered.
- Snippets are extracted from the CI runner checkout.
"""

import json
from pathlib import Path
from typing import Any


def _read_json(path: Path, default: Any) -> Any:
    """
    Read a JSON file and return its parsed content.

    Parameters
    ----------
    path : Path
        Path to the JSON file.
    default : Any
        Value to return if the file cannot be read or parsed.

    Returns
    -------
    Any
        Parsed JSON content, or the provided default value on failure.
    """
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, TypeError, ValueError):
        return default


def _safe_int(value: Any) -> int | None:
    """
    Safely convert a value to an integer.

    Parameters
    ----------
    value : Any
        Value to convert.

    Returns
    -------
    int | None
        Converted integer, or None if conversion fails.
    """
    try:
        return None if value is None else int(str(value))
    except (TypeError, ValueError):
        return None


def _repo_relative_path(p: str | None) -> str | None:
    """
    Normalize a repository-relative file path.

    This helper strips common repository-root prefixes and leading
    path markers so paths render consistently in Markdown output.

    Parameters
    ----------
    p : str | None
        Input path value.

    Returns
    -------
    str | None
        Cleaned repository-relative path, or None if not usable.
    """
    if not p:
        return None

    s = str(p).strip()

    if s.startswith("/repo/"):
        s = s[len("/repo/"):]

    if s.startswith("./"):
        s = s[2:]

    if s.startswith("/"):
        s = s[1:]

    return s or None


def _read_snippet(
    repo_root: Path,
    rel_path: str,
    line: int,
    context: int = 3,
) -> str | None:
    """
    Extract a code snippet around a target line from a repository file.

    Parameters
    ----------
    repo_root : Path
        Root path of the repository checkout.
    rel_path : str
        Repository-relative file path.
    line : int
        Target line number.
    context : int, optional
        Number of surrounding lines to include before and after
        the target line.

    Returns
    -------
    str | None
        Markdown fenced code block containing the snippet,
        or None if the snippet cannot be generated safely.
    """
    try:
        rel_path = _repo_relative_path(rel_path) or ""
        if not rel_path:
            return None

        abs_path = (repo_root / rel_path).resolve()
        repo_root_resolved = repo_root.resolve()

        if (
            abs_path != repo_root_resolved
            and repo_root_resolved not in abs_path.parents
        ):
            return None

        if not abs_path.exists() or not abs_path.is_file():
            return None

        lines = abs_path.read_text(encoding="utf-8", errors="replace").splitlines()

        if line <= 0 or line > len(lines):
            return None

        start = max(1, line - context)
        end = min(len(lines), line + context)
        width = len(str(end))

        buffer = [
            f"{'>' if i == line else ' '} {str(i).rjust(width)} | {lines[i - 1]}"
            for i in range(start, end + 1)
        ]

        return "```text\n" + "\n".join(buffer) + "\n```"

    except (OSError, TypeError, ValueError):
        return None


def _tool_title(tool: str) -> str:
    """
    Return a human-friendly display title for a tool key.

    Parameters
    ----------
    tool : str
        Canonical tool identifier.

    Returns
    -------
    str
        Human-readable tool title.
    """
    return {
        "trufflehog": "TruffleHog (Secrets Detection)",
        "checkstyle": "Checkstyle (Java Style Enforcement)",
        "spotbugs": "SpotBugs (Java Bytecode Analysis)",
        "pmd": "PMD (Java Source Analysis)",
        "semgrep": "Semgrep (Multi-language SAST)",
        "flutter_analyze": "Flutter Analyze (Dart Static Analyzer)",
        "dependency_check": "OWASP Dependency-Check (SCA)",
        "sonar": "Sonar (Quality Gate)",
    }.get(tool, tool)


def _tool_page_name(tool: str) -> str:
    """
    Build the Markdown page name for a tool.

    Parameters
    ----------
    tool : str
        Canonical tool identifier.

    Returns
    -------
    str
        Markdown file name for the tool page.
    """
    return f"{tool}.md"


def _render_enforcement(eval_map: dict, tool: str) -> list[str]:
    """
    Render the enforcement summary block for a tool page.

    Parameters
    ----------
    eval_map : dict
        Lookup map of evaluated tool results.
    tool : str
        Tool identifier.

    Returns
    -------
    list[str]
        Markdown lines for the enforcement section.
    """
    enforcement = eval_map.get(tool)
    if not enforcement:
        return []

    violation = enforcement.get("policy_violation", False)
    blocking = enforcement.get("blocking", False)
    reason = enforcement.get("reason")

    lines = [
        "## Enforcement",
        "",
        f"- **Blocking:**         `{blocking}`",
        f"- **Policy violation:** `{violation}`",
    ]

    if reason:
        lines.append(f"- **Reason:**           `{reason}`")

    lines.append("")
    return lines


def _render_no_findings(tool_result: dict) -> list[str]:
    """
    Render a tool page section for tools with no findings.

    Parameters
    ----------
    tool_result : dict
        Normalized tool result.

    Returns
    -------
    list[str]
        Markdown lines for the no-findings summary.
    """
    vc = tool_result.get("violation_count", 0)
    executed = tool_result.get("executed", False)
    runtime_error = tool_result.get("runtime_error", False)
    meta = tool_result.get("metadata") or {}

    lines = [
        "## Summary",
        "",
        f"- **Executed:**        `{executed}`",
        f"- **Runtime error:**   `{runtime_error}`",
        f"- **Violation count:** `{vc}`",
    ]

    if meta:
        lines += [
            "",
            "## Metadata",
            "",
            "```json",
            json.dumps(meta, indent=2),
            "```",
        ]

    lines += [
        "",
        "_No per-finding detail was provided by the parser for this tool._",
    ]

    return lines


def _render_finding(idx: int, finding: dict, repo_root: Path) -> list[str]:
    """
    Render a single finding entry, including an optional code snippet.

    Parameters
    ----------
    idx : int
        Finding index within the tool page.
    finding : dict
        Normalized finding record.
    repo_root : Path
        Root path of the repository checkout.

    Returns
    -------
    list[str]
        Markdown lines representing the finding.
    """
    message = (
        finding.get("message")
        or finding.get("error")
        or finding.get("title")
        or "N/A"
    )
    file_path = _repo_relative_path(finding.get("file") or finding.get("path"))
    line_number = _safe_int(
        finding.get("line")
        or finding.get("start_line")
        or finding.get("startLine")
    )
    severity = finding.get("severity") or finding.get("level")
    rule = finding.get("rule") or finding.get("check_id")
    rule_url = finding.get("rule_url") or finding.get("helpUri") or finding.get("url")

    lines = [f"### {idx}. {message}", ""]

    if severity:
        lines.append(f"- **Severity:** `{severity}`")
    if rule:
        lines.append(f"- **Rule:** `{rule}`")
    if rule_url:
        lines.append(f"- **Rule URL:** {rule_url}")
    if file_path:
        lines.append(f"- **File:** `{file_path}`")
    if line_number:
        lines.append(f"- **Line:** `{line_number}`")

    if file_path and line_number:
        snippet = _read_snippet(repo_root, file_path, line_number, context=3)
        if snippet:
            lines += ["", "**Code Snippet**", "", snippet]

    lines += ["", "---", ""]
    return lines


def _render_findings(findings: list, repo_root: Path) -> list[str]:
    """
    Render all findings for a tool page.

    Parameters
    ----------
    findings : list
        List of normalized finding records.
    repo_root : Path
        Root path of the repository checkout.

    Returns
    -------
    list[str]
        Markdown lines for the findings section.
    """
    lines = ["## Findings", ""]

    for idx, finding in enumerate(findings, start=1):
        lines += _render_finding(idx, finding, repo_root)

    return lines


def _render_tool_page(
    tool_result: dict,
    eval_map: dict,
    repo_root: Path,
    human_dir: Path,
) -> str:
    """
    Render a complete tool page and write it to disk.

    Parameters
    ----------
    tool_result : dict
        Normalized tool result.
    eval_map : dict
        Evaluated results lookup map.
    repo_root : Path
        Root path of the repository checkout.
    human_dir : Path
        Output directory for human-readable pages.

    Returns
    -------
    str
        Markdown line for the index page linking to the tool page.
    """
    tool = tool_result.get("tool", "unknown")
    findings = tool_result.get("findings") or []
    page_name = _tool_page_name(tool)
    page_path = human_dir / page_name
    title = _tool_title(tool)

    lines: list[str] = [f"# {title}", ""]
    lines += _render_enforcement(eval_map, tool)

    if not findings:
        lines += _render_no_findings(tool_result)
    else:
        lines += _render_findings(findings, repo_root)

    page_path.write_text("\n".join(lines), encoding="utf-8")
    return f"- [{title}](./{page_name}) — {len(findings)} finding(s)"


def _build_eval_map(evaluated_doc: dict) -> dict[str, dict]:
    """
    Merge blocking and non-blocking evaluated results into a single lookup map.

    Parameters
    ----------
    evaluated_doc : dict
        Evaluated results document.

    Returns
    -------
    dict[str, dict]
        Lookup map keyed by tool name.
    """
    eval_map: dict[str, dict] = {}

    for record in evaluated_doc.get("blocking_results") or []:
        if tool_key := record.get("tool"):
            eval_map[tool_key] = record

    for record in evaluated_doc.get("non_blocking_results") or []:
        if tool_key := record.get("tool"):
            eval_map[tool_key] = record

    return eval_map


def generate_human_readable_outputs(
    repo_root: Path,
    analysis_dir: Path,
    normalized_path: Path,
    evaluated_path: Path,
) -> None:
    """
    Generate human-readable Markdown outputs from normalized and evaluated data.

    Parameters
    ----------
    repo_root : Path
        Root path of the repository checkout.
    analysis_dir : Path
        Base analysis directory.
    normalized_path : Path
        Path to normalized.json.
    evaluated_path : Path
        Path to evaluated.json.
    """
    normalized_doc = _read_json(normalized_path, default={})
    evaluated_doc = _read_json(
        evaluated_path,
        default={
            "overall_block": True,
            "blocking_results": [],
            "non_blocking_results": [],
        },
    )

    normalized_results: list[dict] = normalized_doc.get("results", [])
    eval_map = _build_eval_map(evaluated_doc)

    human_dir = analysis_dir / "human"
    human_dir.mkdir(parents=True, exist_ok=True)

    index_lines: list[str] = [
        "# Human-Readable Quality Gate Results",
        "",
        "Per-tool detail pages with findings, file locations, and code snippets.",
        "",
        "## Tool Pages",
        "",
    ]

    for tool_result in normalized_results:
        index_line = _render_tool_page(tool_result, eval_map, repo_root, human_dir)
        index_lines.append(index_line)

    (human_dir / "index.md").write_text("\n".join(index_lines), encoding="utf-8")

    print(f"[humanize] Human-readable pages written to: {human_dir}")
    