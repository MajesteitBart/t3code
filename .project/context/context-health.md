# Context Health

Last audited: 2026-08-08

## Refresh Summary

- Replaced every required Delano starter context file with T3 Code-specific product, architecture, command, safety, testing, and delivery facts.
- Added `source-material.md` for PR #5178 and the user-selected native Android direction.
- Added this health record so contradictions and evidence gaps remain visible during handoff.
- Cross-checked context against `AGENTS.md`, root/app READMEs, `docs/internals/overview.md`, PR #5178 source, current Delano projects, and Git state.
- Assessed the PR #5178 deep-review note against canonical contracts/server behavior and current official Android platform guidance; linked the durable classification from `source-material.md`.
- Refreshed the pack after native Android WS-A with the final toolchain/identity decisions, real Gradle and API-35 emulator evidence, module-reuse outcomes, focused commands, and remaining reserved gates.
- Refreshed the pack after native Android WS-B with canonical source-revision/content-hash fixtures, typed pairing/HTTP/RPC boundaries, one-attempt session ownership, deterministic race coverage, and the real OkHttp compression gate.

## Context Debt Removed

- All generic `<placeholder>` sections in required context files were replaced.
- “Mobile app” ambiguity was replaced with React Native mobile, SwiftUI mobile, and native Android terminology.
- The native Android effort was narrowed from an unbounded parity port to the first architecture-proving foundation project.
- Progress now separates completed setup plus WS-A/WS-B evidence from the 13 remaining project tasks.

## Known Contradictions

- Root `README.md` describes released mobile distribution; `apps/mobile/README.md` still says the React Native client is not distributed. Resolve this before changing release docs.
- The checked-out SwiftUI PR is behind current upstream main. It remains the inspected source reference, not a release-ready branch.
- PR #5178 is open, unmerged, and experimental; any note calling it "shipped" is non-authoritative.
- `active-chat-controls` reports three open tasks, but this work did not audit matching code or evidence.

## Evidence Gaps

- No Xcode build was possible in this Windows environment; SwiftUI test results are source/PR claims.
- Standalone Android CI, release signing/ownership, server integration, accessibility, and measured performance evidence do not exist yet.
- The disposable real-T3 integration harness remains T-017; WS-B interoperability evidence currently uses canonical TypeScript validation plus in-process HTTP/WebSocket servers.
- Verified App Links remain disabled pending owned-host Digital Asset Links and approved signing fingerprints. Android 17 target-SDK work must add and test the new local-network permission path.
- The API-35 emulator proves the foundation root, not low-end hardware performance or later phone/tablet product flows.
- Delano validation for the revised 22-task project is recorded in the research progress artifact after fold-forward.

## Recommended Follow-up

1. Open only a separately authorized dependency-safe task. T-009 may begin WS-C persistence; T-017 may begin the isolated real-T3 integration harness.
2. Re-run `manage-context` after any material protocol, persistence, shell, CI, or release decision.
3. Repair the README distribution contradiction in a separate, evidence-backed docs task.
4. Audit `active-chat-controls` before treating its Delano status as implementation truth.
