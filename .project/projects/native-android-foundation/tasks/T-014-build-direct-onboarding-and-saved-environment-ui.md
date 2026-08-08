---
id: T-014
name: Build direct onboarding and saved-environment UI
status: planned
workstream: WS-D
created: 2026-08-07T13:17:00Z
updated: 2026-08-08T10:32:44Z
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

- [ ] Pasted canonical links, loose host/code input, and `t3code://pair?pairingUrl=...` QR payload text reach the same reviewed pairing flow without exposing credentials in UI or logs.
- [ ] When T-001 confirms an owned/associated T3 web host, its verified App Links enter that flow; otherwise the unavailable association is documented. In all cases unowned web hosts cannot inject a connection route, while arbitrary server URLs remain accepted when explicitly pasted or inside the custom wrapper.
- [ ] Malformed, authorization-rejected, local-network-permission-denied when applicable, unreachable, timeout, cancelled, revoked, and server/transport failures present distinct tested recovery actions with safe trace-ID disclosure.
- [ ] Saved environments can be activated and removed with visible reachability and current selection.
- [ ] Composables construct no HTTP, WebSocket, Keystore, Room, or retry implementation.

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

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:17:00Z: Created from .project/templates/task.md by `delano task add`.
