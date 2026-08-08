# Project Brief

## Problem

PR #5178 adds a standalone native SwiftUI client with transport, persistence, multi-environment state, chat, workspace tools, T3 Connect, extensions, widgets, and platform integrations. Android currently receives the shipped React Native client, but it has no equivalent side-by-side native experiment. A direct port without a delivery contract would risk duplicating shared behavior inconsistently, drifting from wire contracts, and creating an unreviewable change set comparable to the roughly 50,000-line Swift PR.

Delano is being used to turn the native Android choice into explicit architecture decisions, reviewable vertical workstreams, binary acceptance criteria, and durable evidence.

## Target Outcome

Create a separately installable Kotlin/Jetpack Compose T3 client that connects to existing T3 servers directly and through supported remote modes, reproduces the approved native-client capabilities in staged vertical increments, and coexists with `apps/mobile` without regressing existing clients or contracts.

The initial planning baseline established:

- an approved measurable spec and explicit non-goals;
- justified architecture and reuse boundaries;
- rollout, rollback, performance, security, and test strategies;
- dependency-safe workstreams and atomic tasks;
- visible decisions for Android substitutes to iOS-only integrations.

## Scope Boundaries

In scope:

- A standalone native Android application alongside `apps/mobile` and `apps/swift-ios`.
- Kotlin/Compose app foundation, transport, persistence, state, product workflows, T3 Connect, workspace tools, Android integrations, tests, performance, docs, and release preparation.
- Extraction or reuse of existing Kotlin native components where it reduces duplication without coupling the clients incorrectly.
- Contract/server work only where genuine Android parity, such as FCM delivery, requires it.

Out of scope for the planning bootstrap:

- Implementing production application code.
- Replacing or removing the React Native Android client.
- Rebasing or modifying PR #5178.
- Public releases, store submissions, deployments, or new external tracker artifacts.
- Claiming exact visual identity with SwiftUI where Android conventions provide a clearer native behavior.

The planning bootstrap is complete. The 2026-08-08 delivery request superseded the implementation restriction only for WS-A, which now provides the independently buildable application foundation; the remaining scope still requires dependency-safe task authorization.
