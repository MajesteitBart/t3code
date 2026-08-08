---
id: T-014
name: Build direct onboarding and saved-environment UI
status: done
workstream: WS-D
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T17:50:07Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-002, T-006, T-009, T-022]
conflicts_with: [Compose navigation root, onboarding UI]
parallel: true
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-002
acceptance_criteria_ids: [AC-002]
---

# Task: Build direct onboarding and saved-environment UI

## Description

Implement thin Compose screens and route intake for pairing text/custom wrappers/approved App Links, progress/error recovery, saved environments, activation, and removal using WS-C state/actions.

## Acceptance Criteria

- [x] Pasted canonical links, loose host/code input, and `t3code://pair?pairingUrl=...` QR payload text reach the same reviewed pairing flow without exposing credentials in UI or logs.
- [x] When T-001 confirms an owned/associated T3 web host, its verified App Links enter that flow; otherwise the unavailable association is documented. In all cases unowned web hosts cannot inject a connection route, while arbitrary server URLs remain accepted when explicitly pasted or inside the custom wrapper.
- [x] Malformed, authorization-rejected, local-network-permission-denied when applicable, unreachable, timeout, cancelled, revoked, and server/transport failures present distinct tested recovery actions with safe trace-ID disclosure.
- [x] Saved environments can be activated and removed with visible reachability and current selection.
- [x] Composables construct no HTTP, WebSocket, Keystore, Room, or retry implementation.

## Traceability

- Story: US-002
- Acceptance criteria: AC-002

## Technical Notes

- Parsing authority is T-006. This task consumes typed input/result/failure models and owns Android intent delivery plus user-visible recovery only.
- The Swift `PlatformDeepLinkParser` restricts web route injection to T3-owned hosts but its `PairingURL` still accepts arbitrary direct servers. Preserve that distinction.
- The parser accepts canonical `t3code://pair` wrapper text even when the side-by-side development build uses a distinct manifest scheme to avoid fighting the React Native app for OS-level ownership.
- QR camera scanning is out of scope; a copied/scanned QR string and the canonical wrapper must parse through the paste field for deterministic foundation coverage.
- Local-network permission messaging follows the target SDK: explain/request the platform permission only when applicable, and provide settings/retry recovery after denial or revocation.
- Do not display or persist token-bearing raw input after exchange; diagnostics may show a sanitized endpoint and server-provided trace ID, never the token.

## Definition of Done

- [x] Implementation complete
- [x] Tests pass
- [x] Review complete
- [x] Docs updated

## Evidence Log

- 2026-08-08T17:50:07Z: Post-close regression sweep passed after shared UI-composition hardening: final app unit/lint/assembly evidence is 20260808T174711Z.log and final API-35 Compose coverage is 7/7 in 20260808T174835Z.log; real pairing and process-recreation smoke remained successful without credential disclosure.

- 2026-08-08T17:28:52Z: Debug/release app unit tests, warnings-as-errors lint, and assembly passed in .agents/logs/tests/20260808T172522Z.log; five API-35 Compose tests passed in 20260808T172651Z.log; isolated real-server custom-route pairing rendered masked input, saved protected state, and loaded the seeded project; explicit self-review and README scope/App-Link evidence are recorded in the task.

- 2026-08-08T17:28:11Z: `:app:testDebugUnitTest`, `:app:testReleaseUnitTest`, debug/release warnings-as-errors lint, and debug/release assembly passed after final source changes; evidence is logged at `.agents/logs/tests/20260808T172522Z.log`.
- 2026-08-08T17:28:11Z: `foundationInstrumentation` passed five Compose tests on the API-35 `TwentyGoApi35` AVD after final source changes; evidence is logged at `.agents/logs/tests/20260808T172651Z.log`.
- 2026-08-08T17:28:11Z: Real disposable-server smoke used one isolated worktree-local home and captured PID/port ownership. The build-specific custom route rendered its transient input only as password glyphs, direct pairing succeeded, the protected saved environment became current, and the seeded `Android Smoke` project rendered without an offline, revoked, or error surface. No credential was emitted to logs or durable evidence.
- 2026-08-08T17:28:11Z: Self-review checked all five acceptance criteria, manifest route ownership, safe failure/trace presentation, activation/removal actions, lifecycle-owned module composition, and the final phone layout. `FoundationScreen.kt` and `FoundationActions.kt` contain no HTTP, WebSocket, Keystore, Room, transport, or retry implementation; `README.md` records App Link unavailability and foundation scope.

- 2026-08-08T17:05:14Z: Implement the thin direct-pairing and saved-environment UI with reviewed route intake, layered safe recovery, activation/removal, and no transport or persistence construction in composables.

- 2026-08-08T17:05:14Z: Readiness review passed: T-002, T-006, T-009, and T-022 are done; baseline Delano validation is clean; app composition can consume the verified protocol, persistence, supervision, and reconciliation seams.

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
