---
id: WS-C
name: WS-C Persistence and Connection State
owner: team
status: done
created: 2026-08-07T13:12:09Z
updated: 2026-08-08T16:31:01Z
operating_mode: multi-stream
---

# Workstream: WS-C Persistence and Connection State

## Objective

Own protected credentials, versioned environment persistence, collision-safe merged state, active/passive supervision, snapshot reconciliation, and ambiguity recovery.

## Owned Files/Areas

- Proposed `apps/kotlin-android/core-data/`
- Keystore-backed credential repository and deletion/revocation behavior
- Room schema, migrations, environment catalog, active selection, and last-known shell state
- Length-prefixed environment-scoped IDs/routes, state reducers, capability-aware rows, reachability, active/passive reconnect/backoff, resubscription, stale-publish prevention, and lifecycle release
- Process-death, migration, collision, passive last-known retention, and ambiguous-turn recovery tests

## Dependencies

- WS-A app/data module and Android identity decisions.
- WS-B one-attempt session, pairing exchange, contract models, and typed failures.
- Two-process disposable server/receipt support from WS-E for snapshot-recovery and idempotency evidence.

## Risks

- Keystore invalidation and restore behavior varies by device/API.
- Mixing secret and non-secret persistence creates unsafe backup/deletion semantics.
- Multiple retry owners create duplicated subscriptions or commands.
- Cancellation alone may not stop superseded asynchronous work from publishing late state.
- Room migrations can turn experimental data into an accidental compatibility burden unless versioning is explicit.

## Handoff Criteria

- Credentials and environment state have separate tested repositories with credential-first save, catalog-first removal, compensating rollback, and a fail-closed direct-bearer discriminator.
- Two colliding environments merge and route correctly.
- Process death restores last-known state and then reconciles live state.
- Only the active environment owns live subscriptions; passive refresh retains last-known rows and cannot disrupt the shared active session.
- Reconnect/resubscribe has one owner, bounded jittered backoff, deterministic cancellation, fresh request IDs, and no duplicate collectors or stale publishes.
- Stable turn identity is verified from a fresh snapshot before replay, with one accepted receipt/domain effect in the integration fixture.
