# Quality & Security Enforcement Subsystem

## Overview

The `quality/` directory contains the automated security and quality enforcement subsystem used by CI.

This subsystem is responsible for:

- Secrets detection
- Static analysis (SAST)
- Dependency analysis (SCA)
- Policy evaluation
- Merge approval/block decisions
- Artifact generation and reporting

The enforcement engine is located at:

```
quality/ci/gate/

```

All tool execution artifacts are written to:

```
quality/analysis/

```

---

## Sandbox Fixtures (Integration Phase Only)

During CI gate development and validation, controlled fixtures may exist under:

```
quality/sandbox/

```

These fixtures provide deterministic:

- Passing scenarios
- Failing scenarios
- Report validation
- Merge blocking validation

The directory is **not application code**.

---

## Environment Control

All scanners are scoped by:

```
SCAN_ROOT

```

Examples:

```
SCAN_ROOT=quality/sandbox/passing
SCAN_ROOT=quality/sandbox/failing
SCAN_ROOT=.

```

Production enforcement must use:

```
SCAN_ROOT=.

```

---

## Production Cutover Procedure

Before enabling production enforcement:

1. Remove sandbox fixtures:

```
git rm -r quality/sandbox

```

2. Remove this README (if it references sandbox validation).

3. Commit removal.

4. Set:

```
SCAN_ROOT=.

```

5. Validate full repository scan passes.

---

## Production Policy

- No sandbox fixtures may exist.
- CI must scan real application code only.
- No conditional bypass logic is permitted.
- The Python gate engine is the single enforcement authority.

---

## Ownership

Changes to this subsystem must:

- Preserve deterministic enforcement behavior.
- Maintain raw → normalized → evaluated traceability.
- Keep policy logic centralized in the gate engine.

---
