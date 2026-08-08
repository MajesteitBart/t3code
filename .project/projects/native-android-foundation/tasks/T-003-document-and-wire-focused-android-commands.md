---
id: T-003
name: Document and wire focused Android commands
status: done
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T13:02:06Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002]
conflicts_with: [package.json, README command sections]
parallel: true
priority: medium
estimate: S
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Document and wire focused Android commands

## Description

Expose stable local build, unit-test, lint/static, instrumentation, and install entry points and document prerequisites without adding repo-wide checks.

## Acceptance Criteria

- [x] The native Android README lists exact prerequisites and focused build, test, lint, instrumentation, and install commands.
- [x] Each documented command resolves from the documented working directory.
- [x] Root task wiring, if added, invokes only native Android scope.
- [x] No command accesses live T3 home data or prints signing and credential values.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- Reserve distinct focused commands for assemble/install, unit/static checks, AC-007 contract conformance, integration harness, instrumentation/accessibility, and AC-008 performance evidence; do not hide all gates behind one repo-wide command.
- Commands that start helpers must expose/capture exact PIDs and isolated home/port inputs so T-017 can stop only what it starts.
- Redact access credentials, pairing codes, WebSocket tickets, App Link signing material, and private paths from example output.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T13:02:06Z: foundationAssemble, foundationUnitTest, foundationStaticCheck, foundationInstallDevelopment, and foundationInstrumentation passed; reserved gates resolve via dry-run and fail closed until their owners implement evidence.

- 2026-08-08T13:01:30Z: `apps/kotlin-android/README.md` documents JDK 17, API 36, Build Tools 35.0.0, platform-tools/device prerequisites, the exact working directory, Windows and Unix wrappers, unsigned release-shaped behavior, and focused build/unit/lint/install/instrumentation commands.
- 2026-08-08T13:01:30Z: Root Gradle tasks `foundationAssemble`, `foundationUnitTest`, and `foundationStaticCheck` ran successfully. They delegate only to explicit `:app`, `:core-data`, `:core-protocol`, and `:core-testing` debug/release tasks; no repository-root wiring or repo-wide gate changed.
- 2026-08-08T13:01:30Z: On the API-35 `TwentyGoApi35` AVD, `foundationInstallDevelopment` installed `com.t3tools.t3code.compose.dev` and `foundationInstrumentation` completed one connected Compose test successfully. The captured emulator process tree was stopped after validation.
- 2026-08-08T13:01:30Z: `gradlew.bat tasks --group build`, `--group install`, and `--group verification` each passed and list every documented entry point. `foundationContractConformance foundationIntegration foundationAccessibility foundationPerformance --dry-run` passed, proving all reserved commands resolve without executing their deliberately fail-closed actions.
- 2026-08-08T13:01:30Z: Reserved commands name T-005/T-017/T-016/T-018 ownership and throw until real evidence replaces them. README examples contain no home path, port, signing material, credential, pairing code, or WebSocket ticket value and require any future helper to use isolated inputs plus exact captured PIDs.
- 2026-08-08T13:01:30Z: Self-review confirmed every documented command starts in `apps/kotlin-android`, uses the checksum-pinned wrapper, and neither depends on nor invokes Expo, Metro, React Native generation, or live T3 developer data.

- 2026-08-08T12:56:22Z: Document and expose only the verified standalone Android commands, with reserved fail-closed gates for follow-on contract, integration, and performance work.

- 2026-08-08T12:56:22Z: T-002 is done; the standalone Gradle tasks are build-proven and no conflicting root command files have been changed.

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
