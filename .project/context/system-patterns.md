# System Patterns

## Server-Owned Execution

Clients send typed commands and subscribe to server state over authenticated Effect RPC WebSockets. Provider processes, filesystem access, terminals, Git operations, and checkpoints remain on the server. A native Android client must remain a control surface, not introduce local execution paths.

## Event-Sourced Orchestration

The server decider produces persisted events; projectors derive read models. Commands use durable receipts for idempotency, and async reactors expose drains/typed receipts for deterministic testing. Client retries must preserve stable command identifiers so ambiguous network outcomes do not duplicate work.

## Connection Supervision at the Client Boundary

One transport attempt does not own retry policy. Supervisors handle environment lifecycle, backoff, reachability, resubscription, last-known data, and active/passive connection behavior. Multi-environment identifiers must be scoped to prevent collisions.

## Thin UI, Explicit Native Adapter

Transport, authentication, persistence, retry, and domain state remain outside composables/views. The SwiftUI app uses `FeatureClient`; web and React Native use `packages/client-runtime`. Native Android should define a similarly testable boundary and avoid a single adapter object accumulating unrelated feature logic.

## Contract Conformance Without Dual Truth

`packages/contracts` is canonical. Native clients necessarily model the wire shape in their language, but conformance fixtures should be derived from or checked against canonical TypeScript schemas. Hand-copied Kotlin models are not a second contract authority.

## Platform-Native Outcome Parity

Match user outcomes and server semantics while using native platform conventions. Examples: Android App Links for Universal Links, WorkManager for background refresh, Glance for widgets, and grouped/ongoing notifications for Live Activity-like awareness.

## Conservative Delivery

Delano lifecycle state lives in `.project`, runtime assets in `.agents`, and compatibility adapters stay thin. Installation or upgrades preserve repository-owned project/context state. External tracker writes, public artifacts, commits, pushes, and PRs require explicit approval.
