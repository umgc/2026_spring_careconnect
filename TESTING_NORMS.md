# Testing Norms: Mock Data Framework

## Purpose
1Define the shared conventions for mock-data-driven tests so multiple contributors can add tests without introducing inconsistent patterns or unintentional scope changes.

## Scope
- Backend unit tests under `/backend/core/src/test/java`.
- Frontend unit/widget tests under `/frontend/test`.
- Test-only fixtures, fakes, and helper bootstrap utilities.

## Non-Goals
- Refactoring unrelated deprecated areas.
- Fixing unrelated production defects.
- Changing product behavior to satisfy test convenience.

## Core Rules
1. Prefer shared fixtures over inline ad-hoc object setup.
2. Unit tests must avoid live DB/network dependencies.
3. Keep production code untouched unless a minimal, behavior-neutral seam is required.
4. Document non-obvious test setup with concise comments.

## Commenting Convention (Code)
1. File-level comment:
- Explain what the helper/test suite validates.
- Clarify why dependencies are mocked or faked.

2. Function-level comment:
- Explain scenario represented and defaults chosen.
- Note when to use the helper.

3. Test-body comments:
- Use `Arrange`, `Act`, `Assert` in non-trivial tests.
- Add comments where setup values are intentionally meaningful.

## Folder Conventions
- Backend fixtures: `/backend/core/src/test/java/com/careconnect/testsupport/fixtures`
- Frontend fixtures/helpers: `/frontend/test/test_support`

## Initial Adoption Targets
- Backend: `TaskServiceV2Test`, `TaskControllerV2Test`
- Frontend: `patient_dashboard_name_test.dart`, `jwt_auth_test.dart`

## Review Checklist
1. Is the change test-only?
2. Does it use shared fixtures/helpers?
3. Are comments explaining intent present but not noisy?
4. Does it avoid unrelated code repair work?
