---
id: T-004
name: Decide reuse boundaries for existing Kotlin modules
status: done
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T12:55:57Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: [apps/mobile/modules]
parallel: true
priority: medium
estimate: M
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Decide reuse boundaries for existing Kotlin modules

## Description

Audit the terminal, review diff, composer, and native controls modules for Expo, JNI, resource, and toolchain coupling; record extract, adapt, or defer decisions.

## Acceptance Criteria

- [x] The decision log records extract, adapt, or defer for each existing Android native module.
- [x] Each decision identifies Expo, JNI, resource, license, and toolchain coupling with source references.
- [x] Any proposed extraction retains a thin Expo adapter and a focused React Native compatibility check.
- [x] No production module is moved solely to satisfy this audit task.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- The Swift feature inventory is evidence for later project families, not permission to extract terminal/review/composer code during foundation.
- Weight the audit toward ordinary Android library boundaries, Expo/JNI/resource/toolchain coupling, and whether a thin existing React Native adapter can remain. Do not preselect Ghostty or any UI module solely because a future parity area may use it.
- Record follow-on ownership and prerequisites in T-020; leave implementation/decomposition to an approved project.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T12:55:57Z: D-015 covers Expo, JNI, resources, licenses, toolchains, source references, and explicit defer/adapt outcomes; T-020 owns all prerequisites; delano validate passed.

- 2026-08-08T12:55:39Z: D-015 records one disposition for every existing Android module: terminal defer, review-diff adapt, composer-editor adapt, and native-controls defer/Compose-native replacement. Each entry identifies Expo, JNI, packaged resource, license, and inherited/native toolchain coupling with file-and-line source references.
- 2026-08-08T12:55:39Z: Adapter-first gates are explicit for both adapt candidates: keep the Expo module thin and pass focused React Native prop/event/command/render compatibility checks before switching consumers. The deferred terminal path adds Ghostty revision, NDK/CMake/Zig, four-ABI, 16-KiB-page, resource, license, and RN compatibility prerequisites.
- 2026-08-08T12:55:39Z: `rg --files` plus focused coupling searches covered all four module trees, build files, manifests, Expo configs, Kotlin/C++ sources, JNI libraries, assets, build scripts, and license/notices. No module-local test source sets were found, so D-015 makes compatibility coverage a prerequisite rather than claiming existing proof.
- 2026-08-08T12:55:39Z: T-020 now owns the later proceed/revise/stop decision and repeats the approved prerequisites without activating or decomposing follow-on work. No file under `apps/mobile/modules/` or `native/libghostty-vt/` changed.
- 2026-08-08T12:55:39Z: `delano validate` passed with zero errors; the sole warning is expected dirty `.project` provenance during execution. Self-review confirmed every required coupling dimension and the no-production-move boundary are represented.

- 2026-08-08T12:52:41Z: T-001 is done and the four existing Kotlin module trees are available for a read-only extraction-boundary audit.

- 2026-08-08T12:30:56Z: T-001 is done; the existing-module audit is read-only, bounded, and dependency-safe.

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
