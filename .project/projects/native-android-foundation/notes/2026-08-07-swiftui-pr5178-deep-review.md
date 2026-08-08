---
name: Deep review — improvements deduced from the SwiftUI app (PR #5178)
slug: swiftui-pr5178-deep-review
project: native-android-foundation
created: 2026-08-07
sources:
  - .project/projects/native-android-foundation/ (spec, plan, decisions, WS-A..E, T-001..T-020)
  - apps/swift-ios/ on branch pr-5178-swift-ios (Core, App/Cloud, App/Platform, Features, DesignSystem, Tests)
status: assessed
assessment: ../research/swiftui-pr5178-review-assessment/findings.md
---

# Deep Review: Native Android Foundation vs. the shipped SwiftUI app

> Assessment note (2026-08-08): this is preserved as review input, not an executable contract. PR #5178 is an open experimental client, and several claims below are corrected, narrowed, or rejected in `../research/swiftui-pr5178-review-assessment/findings.md`. Canonical project artifacts contain only the accepted/adapted conclusions.

## Verdict

The foundation plan is architecturally sound and its bounded scope should be kept. The
layering decisions (one-attempt transport, supervisor-owned retry, scoped IDs,
secrets/state separation, canonical TypeScript contracts) match what the SwiftUI app
actually converged on after shipping. Nothing in PR #5178 invalidates D-001..D-007.

However, the plan was written _about_ PR #5178 without extracting its ground truth. All
20 task files have empty Technical Notes, and the Swift code now answers most of the
plan's open protocol questions precisely. The improvements below fall into four groups:

- **A. Protocol facts to bake into the tasks now** (deduced from `apps/swift-ios/Core` — cheap to record, expensive to rediscover).
- **B. Behavioral corrections** where the plan's assumptions diverge from what iOS actually does.
- **C. Plan-quality fixes** found in the deep review of the delano bundle itself.
- **D. Follow-on roadmap reshaping** based on the full iOS feature inventory.

---

## A. Protocol ground truth to record in task Technical Notes

These are facts, not proposals. Each should be copied into the named task before
implementation starts, and each should become a T-005 fixture or a transport test.

### A1. Pairing is RFC 8693 token exchange (T-006)

- Descriptor: unauthenticated `GET /.well-known/t3/environment` →
  `{environmentId, label, platform{os,arch}, serverVersion, capabilities{…}}`.
- Exchange: `POST /oauth/token`, form-encoded, `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`,
  `subject_token=<pairing code>`, `subject_token_type=urn:t3:params:oauth:token-type:environment-bootstrap`,
  `requested_token_type=urn:ietf:params:oauth:token-type:access_token`, plus
  `client_device_type=mobile`, `client_os`, optional `client_label`. Android must send
  `client_os=Android` and its own label — the server records these in
  `/api/auth/clients` device rows.
- Response `token_type` must equal `Bearer` for direct pairing; validate it.
- Persistence ordering invariant (see B4): credential saved to Keystore **before** the
  catalog upsert; a failed catalog write deletes the credential. Reverse order on
  removal. (Swift: `PairingService.pair`, `EnvironmentRuntime.remove`.)

### A2. Pairing URL grammar (T-006, T-014)

Accepted forms (Swift `PairingURL`): `https://host/pair#token=<code>` (token from
fragment first, then query; optional `label`, `host` params); deep-link wrapper
`t3code://pair?pairingUrl=<encoded>` (QR parity with the React Native client — Android
must accept the same wrapper); loose `host code` pairs (`^[A-Za-z0-9_-]{4,256}$`);
bare hosts defaulting to `https://`. One input yields both bases via ws(s)↔http(s)
scheme swap. The plan currently has **no task for Android intent filters**, yet pairing
links are the core onboarding input — see C8.

### A3. HTTP surface used by the foundation shell (T-005, T-006)

`GET /api/orchestration/shell` (shell snapshot), `GET /api/orchestration/snapshot`,
`GET /api/orchestration/threads/{id}?turnLimit=&beforeCursor=` (paginated),
`POST /api/orchestration/dispatch` → `{sequence}`, `POST /api/auth/websocket-ticket` →
`{ticket, expiresAt}`, `GET /api/auth/session`, `GET /api/auth/clients`,
`POST /api/auth/clients/revoke`, `POST /api/auth/clients/revoke-others`.
Error body: `{message?, reason?, traceId?}` — carry `traceId` into typed failures.
Send `Accept-Encoding: gzip` explicitly (the server only compresses when advertised;
OkHttp does this transparently — verify decompressed decode in a fixture test).

### A4. Effect RPC wire framing (T-007) — fills the plan's biggest unknown

- Endpoint: WS base + `/ws`, one-use ticket in query `?wsTicket=…`, minted via HTTP on
  **every** connection attempt (Swift test: reconnects re-mint every time).
- Client→server: `{"_tag":"Request","id":<monotonic int>,"tag":"<method>","payload":{…},"headers":[[]]}`,
  plus `Ping`/`Pong`, `Ack {requestId}` after every consumed chunk, `Interrupt {requestId}` for cancel.
- Server→client (`_tag`-discriminated): `Chunk {requestId, values[]}`,
  `Exit {requestId, exit{_tag, value?, cause?}}` (error message at `cause[0].error.message|detail`),
  `Pong`, and fatal `Defect` / `ClientProtocolError`.
- Keepalive: client Ping every 5 s. Handshake offers `permessage-deflate;client_max_window_bits`
  (OkHttp can actually verify negotiation, which URLSession cannot — add that assertion).
- Subscribe payloads: `orchestration.subscribeShell {requestCompletionMarker:true, afterSequence?}`;
  `orchestration.subscribeThread {threadId, requestCompletionMarker:true, afterSequence?, turnLimit?}`.
  Shell stream items: `synchronized|snapshot|project-upserted|project-removed|thread-upserted|thread-removed`
  (deltas carry `sequence`); thread items: `synchronized|snapshot|event`.
- Foundation method list (subset used by the shell): `server.probe`, `server.getConfig`,
  `orchestration.dispatchCommand`, `orchestration.subscribeShell`, `orchestration.subscribeThread`,
  `subscribeServerConfig`. The full catalog (projects._, vcs._, terminal._, review._,
  assets.createUrl, git.runStackedAction, sourceControl.\*) is follow-on scope but the
  T-005 inventory should list it so fixtures are additive.

### A5. Replay/fallback semantics (T-007, T-008, T-011) — contract-critical

- A unary request **sent** over a socket that then breaks fails permanently
  (`disconnected`) and is never replayed — side effects are ambiguous.
- A request that never crossed the socket fails `connectionUnavailable` after a
  bounded wait (Swift: 4 s), and that is the **only** error class allowed to trigger
  HTTP dispatch fallback.
- The message-first bootstrap command (`thread.turn.start` + `bootstrap`) is
  **WebSocket-only, never HTTP-fallback** (Swift test pins this).
- Subscriptions resubscribe on reconnect with **fresh request ids**; one-shot
  subscriptions (e.g. `git.runStackedAction`) must instead fail on disconnect — never
  replay a commit/push.
- Send `Interrupt` on cancel only if the request's connection id matches the current
  connection — an Interrupt after reconnect could kill an unrelated request reusing the id.
- Backoff: `min(5.0, 0.35 * 1.7^(retry-1))` seconds × random jitter `0.5…1.0`. The
  jitter exists specifically to desynchronize ticket mints across multiple
  environments after a server restart — keep it in the Kotlin supervisor (T-011).

### A6. Command grammar and stable identity (T-005, T-013)

Every command: `{type, commandId: UUID, threadId?, createdAt: ISO-8601, …}`. Foundation
needs `thread.create`, `project.create`, `thread.turn.start` (message
`{messageId, role:"user", text, attachments[]}`, `runtimeMode`, `interactionMode`,
optional `modelSelection`), plus the lifecycle set (`thread.archive/unarchive/delete`,
`thread.meta.update`, `thread.turn.interrupt`, `thread.approval.respond`,
`thread.user-input.respond`, settle/snooze/pin variants). Enums:
`RuntimeMode = approval-required|auto-accept-edits|auto|full-access`,
`InteractionMode = default|plan`. `ModelSelection` has legacy aliases (`provider` key,
map-shaped `options`) that fixtures must cover.

### A7. JSON fidelity and forward compatibility (T-005, T-010)

- Sequence numbers must be `Long`, never `Double` (Swift keeps Int64/UInt64 cases in its
  JSON value type and tests round-tripping large integers). kotlinx-serialization's
  default `JsonPrimitive` handling is fine, but any generic `JsonElement → model`
  bridging must not route through Double.
- Deterministic encoding for fixtures: sorted keys (Swift uses `sortedKeys` +
  `withoutEscapingSlashes`).
- Forward-compat posture everywhere: unknown thread event types → full refresh;
  optional capability flags decoded leniently; per-element lossy decoding for
  provider lists. T-005's fixture classes should add **capability-absent** and
  **unknown-stream-item** fixtures beyond the four already planned.

### A8. Feature gating via descriptor capabilities (T-005, T-010, T-015)

`EnvironmentDescriptor.capabilities` gates per-environment features:
`repositoryIdentity, connectionProbe?, threadSettlement?, threadSnooze?, threadPinning?,
threadTitleRegeneration?, serverSelfUpdate?…` and `ServerConfig.threadSnapshotPagination`.
The foundation's reducers (T-010) should model capabilities per environment from day
one so follow-on features gate cleanly per server version.

---

## B. Behavioral corrections — where the plan diverges from shipped reality

### B1. T-013's idempotency model is wrong-shaped: it's client-verified commit, not a server receipt

The plan assumes "server receipt idempotency" — retry with the same commandId and expect
the server to dedupe, proven by "one accepted receipt." The shipped iOS app does **not**
rely on that. Its recovery contract (`NativeRetryIdentityTests`):

- `CommandIdentity {commandID, messageID, createdAt}` fixed at first attempt, reused verbatim.
- On ambiguous failure, the client fetches the thread snapshot and checks whether
  `messageID` was committed. Committed → success; absent → rethrow **keeping the identity**.
- Partial bootstrap recovery: thread exists in the right project with zero messages →
  re-send only the final `thread.turn.start` with the same identity.
- Identity resets only after a **fresh shell snapshot confirms the thread is absent**
  (and the generated worktree branch is reclaimed without `force`).

Rewrite T-013 to prove _this_ contract (commit-verification + stable identity across
retry) instead of assuming a server-side dedupe receipt API that may not exist. This
also resolves the plan's unexamined risk that T-013 would otherwise force a server
change, which the spec restricts to a "narrowly approved compatibility correction."

### B2. "One supervisor per environment" should not mean "one socket per environment"

The plan reads as N live WebSocket sessions. iOS ships **active/passive** supervision:
only the active environment holds subscriptions (shell + server-config + thread
detail); passive environments are polled over HTTP every 20 s with a 6 s timeout, and
failed reads **keep last-known rows** ("one offline machine cannot empty home"). This
is materially better for battery/network on mobile and simplifies T-011. Record it as
a decision (extend D-005) and shape T-011/T-012/T-015 accordingly: supervisors exist
per environment, but only the active one owns a socket; passive reachability comes
from the poll loop.

### B3. HTTP fallback while the socket is down: fresh data, "reconnecting" presentation

While the socket is disconnected but HTTP works, iOS keeps polling and publishes
snapshots flagged as not-source-connected so the UI shows "reconnecting" with fresh
data rather than an offline wall. Add this dual-channel behavior to T-011 and to
T-015's state presentation (the plan's offline/reconnecting states currently imply
data staleness tracks connectivity — it shouldn't).

### B4. Persistence invariants for T-009, verbatim from iOS

- Save: credential first, catalog second; roll back the credential if the catalog
  write fails. Remove: catalog entry first, credential second — a catalog row must
  never point at a missing credential, and a destroyed credential must never leave a
  dangling catalog row. Implement as a compensating-rollback pair with collected
  rollback errors (Swift `EnvironmentPersistenceError`).
- Client replacement on endpoint change: publish the replacement connection **before**
  disconnecting the stale one, under a per-environment mutex (Swift hit an actor
  reentrancy bug where remove-then-insert let a concurrent caller build a duplicate).
- Ephemeral one-shot clients for probes so a passive reachability check can never
  disconnect the shared session.
- Credential model carries `authorizationMethod` (`bearer|dpop`) with strict binding
  checks (kind ⇔ method ⇔ managedEnvironmentID ⇔ thumbprint) so a future managed
  credential can never silently downgrade to bearer. Room/Keystore schema should
  include the method field **now** (defaulted to `bearer` for legacy rows) to avoid a
  migration when T3 Connect lands.

### B5. Scoped-ID algorithm for T-010 exists — copy it

iOS scopes UI ids as `"<kind>:<utf8len(envID)>:<envID><wireID>"` for
`project|thread|approval|input` — length-prefixed so ids stay unambiguous even when an
environment id contains `:`. It additionally resolves **raw wire ids** only when
exactly one environment owns that id (fixture/deep-link compatibility), and keeps
uiID→envID and uiID→wireID indexes covering archived and provisional (just-created)
threads. Adopt the same encoding and the same "route object" pattern
(`{uiID, wireID, environmentID, client}`) so every operation is routed to its owner.

### B6. Staleness guards: generation counters, not just cancellation

The plan says "no duplicate collectors" but doesn't name the mechanism. iOS uses
generation counters everywhere (environment generation, detail-stream generation,
history epoch, refresh id): capture before `await`, verify before publish. In Kotlin
this maps to generation tokens checked after every suspension (structured concurrency
alone does not prevent a superseded coroutine from publishing late results between
cancellation checks). Name this pattern in T-010/T-011 acceptance criteria.

### B7. Reachability probing without iOS's permission problem

iOS's local-network probe is a real descriptor GET with error triage
(timeout / unreachable-host / permission-denied / server-rejected / transport).
Android has no Local Network permission, so drop the EPERM sniffing but **keep the
triage taxonomy** — it directly feeds T-014's per-failure recovery actions and is the
missing "error taxonomy" the plan demands but never defines (see C5).

### B8. Adopt the Swift test suite's pinned behaviors as Android acceptance criteria

The iOS tests define the de facto native-client contract. Add these to T-008/T-011/
T-013 ACs: exact command JSON shapes against fixtures; WS-only bootstrap; fresh one-use
ticket per reconnect attempt; no replay after send; Interrupt-on-cancel with
connection-id match; stable retry identity with confirmed-absence reset; last-known
rows retained per failed environment; duplicate wire-id disambiguation across
environments; gzip offer + decode; large-integer JSON fidelity.

---

## C. Plan-quality fixes (deep review of the delano bundle itself)

1. **Empty Technical Notes on all 20 tasks.** Highest-leverage fix: paste sections A/B
   above into the relevant tasks (T-005/006/007/008/009/010/011/013 especially).
   T-007 and T-011 are XL tasks whose implementers currently must reverse-engineer the
   wire protocol from `packages/client-runtime` and Swift source.
2. **Traceability misalignments.** T-007 maps to AC-004 (a supervision criterion) while
   explicitly forbidding internal reconnect; T-011 pairs US-003 with AC-004; T-016/T-018
   map to US-003 despite being a11y/perf tasks. There is no AC at all for contract-drift
   detection (US-004) or for performance (NFR-001), yet T-019 claims every AC maps to a
   command. Add AC-007 (fixture drift fails a focused command) and AC-008 (startup/list
   baselines recorded within thresholds), and re-point the task mappings.
3. **T-016/T-018 baseline circularity.** T-016 must pass "the recorded recomposition and
   frame-time baseline," which T-018 records, but neither depends on the other. Make
   T-018's baseline recording a dependency of T-016's threshold assertion, or move
   baseline capture into T-015's definition of done.
4. **T-017 fixture ambiguity.** The harness must provide "two environments with
   colliding raw identifiers" yet captures "the exact spawned PID" (singular). Decide
   now: one server process hosting two environments, or two spawned servers (PIDs
   plural, two ports). The Swift fixtures suggest per-environment servers; whichever is
   chosen, the harness AC must say so.
5. **Missing error taxonomy.** T-006/T-007 demand "typed failure categories" and WS-D
   must map them to recovery actions, but no file defines the set. Adopt:
   `malformed | expired | revoked | unreachable | permissionDenied(n/a Android) |
serverRejected(status, message, traceId) | timeout | cancelled | disconnected |
connectionUnavailable | transport` — the union of Swift's HTTP, probe, and RPC
   failure classes. Put it in decisions.md so WS-B and WS-D share one vocabulary.
6. **Revocation detection is unspecified.** T-012 tests revocation recovery but no task
   says how the client learns a credential is revoked (401 mapping on HTTP, ticket
   mint failure, or a WS close code). iOS treats a 401 as "check for newer saved
   credential, then re-auth once, then surface revoked." Specify the same single-retry
   rule for Android and forbid unbounded re-auth loops.
7. **Critical path stacks three XLs sequentially** (T-005→T-007→T-011→T-015). Split
   T-007 into framing/serialization vs. session lifecycle, and T-011 into supervisor
   core vs. reconciliation — the A4/A5 facts above make the split lines obvious, and
   the smaller tasks parallelize across WS-B/WS-C.
8. **No deep-link/intent-filter task.** WS-A mentions "deep-link placeholders" once.
   Pairing links (`https://t3.codes/...`, `t3code://pair?...`) are the primary
   onboarding input and QR payloads use the deep-link wrapper. Add intent filters +
   URL-grammar parsing to T-006/T-014 ACs, including iOS's security rule: connection
   routes are **rejected from untrusted web hosts** (only `t3.codes` domains may carry
   pairing parameters).
9. **Branch skew risk.** tech-context notes this branch is based on PR #5178 and not
   rebased onto newest main. T-005's fixture exporter must record the contracts commit
   it was generated from, and CI should fail when `packages/contracts` moves without
   regenerating fixtures.
10. **QR scanning is absent from T-014.** iOS onboarding treats QR scan as a primary
    method (with paste fallback). Camera scanning can stay out of the foundation gate,
    but T-014 should at least accept the QR payload format via paste so the flow is
    testable, and the follow-on project should add CameraX scanning.

---

## D. Follow-on roadmap reshaping (from the full iOS feature inventory)

The foundation gate (T-020) must scope follow-on projects. The iOS app defines what
"parity" concretely means; these are the deduced projects, roughly in dependency order,
with the design facts worth carrying over.

### D1. Chat/transcript + composer (the largest project)

- Transcript = recycled list (Android: RecyclerView/LazyColumn with stable keys) with
  delta-driven updates (append fast path / changed-message reconfigure / full rebuild
  fallback), pagination ("Load earlier turns", `turnLimit` 10 initial / 20 per page,
  visible-anchor restoration on prepend), bottom-pinning only while the reader follows
  the latest turn (viewport geometry is unit-tested on iOS — port those tests).
- Markdown: iOS wrote a dependency-free GFM block parser + content-addressed render
  cache (bounded, memory-warning aware, inline runs shared by reference across
  streaming revisions) + streaming renderer with 150 ms throttle and byte-prefix
  staleness rule. Android should budget for the same three-part design
  (parser / cache / streaming) rather than assuming a library solves it.
- Event reduction: pure reducer mapping thread events
  (`thread.message-sent` with streaming text-delta concatenation,
  `thread.activity-appended`, `thread.session-set`, `thread.turn-diff-completed`,
  `thread.reverted`→refresh, unknown→refresh) with monotonic sequence enforcement and
  coalesced publishes (80 ms deltas / 250 ms snapshot refreshes).
- Composer: per-project and per-thread durable drafts with write-fencing (async
  restore never clobbers live typing), offline outbox with optimistic queued bubbles
  and backoff drain, attachments embedded as base64 data URLs in the command (10 MiB,
  ≤8 images, image/\* only) with `assets.createUrl` signed URLs for download, approval
  panel + multi-question user-input wizard replacing the composer, typed triggers
  (`/` commands, `$` skills, `@` server-backed file search), explicit-button-only send.

### D2. Message-first daily UX (can start earlier than expected)

The home-shelf logic is pure and portable now: Pinned / Active (creation-ordered, no
re-sort on activity) / Snoozed (auto-wake on approval-needed, new failure, or turn
completion) / Settled (explicit + auto-settle after 3 days of terminal-state
inactivity) / Archived; single-next-boundary refresh scheduling; status vocabulary
(Working/Approval/Input/Failed/Done/Ready). Mobile normalizes runtime mode to
`full-access` and interaction mode to `standard`. **Improvement:** design T-010's
reducers and T-015's row model so shelves and status labels drop in without rework —
e.g., thread rows should already carry status, pinned/snoozed/settled flags, and
per-environment reachability, even if the foundation shell renders only a flat list.

### D3. T3 Connect / cloud relay (server work required for Android)

iOS's stack: Clerk sign-in → relay DPoP token exchange (`POST /v1/client/dpop-token`,
account-partitioned cache) → per-environment bootstrap credential
(`POST /v1/environments/{id}/connect`, device-thumbprint-bound) → environment DPoP
token via the same `/oauth/token` exchange. DPoP = ES256/P-256 key in
Keychain-equivalent (Android Keystore EC key, never rotated), fresh proof per request,
single-flight refresh per environment, descriptor-id cross-check to defeat
relay-swapped endpoints. Two Android-blocking facts deduced from the code:
`POST /v1/mobile/devices` requires `platform:"ios"` + iOS≥18 and APNs-specific fields
(`apsEnvironment`, `pushToStartToken`) — the relay needs `platform:"android"` + FCM
fields before this project can start. Record that server dependency in T-020's
follow-on scoping; it confirms and sharpens the spec's "APNs-shaped relay" probe note.

### D4. Platform integrations

Deep links (routes: connection/environment/project/thread/newTask with env-scoped and
raw-id resolution preferring the active environment; one-shot route mailbox for
cold-start), share target (durable inbox in shared storage, draft-import idempotent by
share id, cleared only after the durable draft write), app shortcuts (new task, recent
threads mirrored to a small store), notifications (local for thread transitions while
backgrounded, threaded by thread id, carrying a `t3_route` payload; permission prompt
gated behind an explicit settings save), background refresh (WorkManager analog of the
15-min BGAppRefreshTask), haptics gated by settings with a transition classifier
(completed→success, needs-approval/input→warning, failed→error; foreground=haptic,
background=notification), and **agent awareness**: an aggregate projection of ≤5
active/recent tasks (attention > failed > running > done) feeding widgets — Android's
analog is Glance widgets + an ongoing notification instead of Live Activities/Dynamic
Island.

### D5. Workspace tools

Files browser (server-side listing, previews, signed asset URLs), review diff (word-level
spans, full-context hydration, tap-line review comments sent as agent prompts), source
control (server-advertised action set), terminal (Ghostty — the RN Android Ghostty
module makes T-004's reuse audit directly load-bearing for this project; weight the
audit toward proving that module's extractability).

### D6. Design-token alignment (foundation shell can adopt now)

Dark-first pure-black theme (surfaces as low-alpha white overlays), semantic-only
typography roles (no one-off sizes — maps to Material type scale discipline), 44 pt/dp
minimum targets, 760 pt/dp reading width cap, provider brand icons with
initial-letter fallback, deterministic per-project badge colors. None of this
contradicts D-007 (Android-native interaction); it is visual identity, not interaction
mechanics, and adopting the tokens in the foundation shell avoids a later reskin.

---

## Suggested immediate actions (smallest set, in order)

1. Paste A1–A8 into the Technical Notes of T-005, T-006, T-007, T-008, T-010, T-011.
2. Rewrite T-013 per B1 (commit-verification idempotency, not server receipts).
3. Amend D-005 with the active/passive supervision model (B2) and add the error
   taxonomy (C5) plus the 401 single-retry revocation rule (C6) to decisions.md.
4. Add AC-007 (contract drift) and AC-008 (performance baseline); fix the T-007/T-011/
   T-016/T-018 traceability and the T-016↔T-018 dependency (C2, C3).
5. Extend T-006/T-014 with the pairing-URL grammar, intent filters, trusted-host rule,
   and QR-payload paste acceptance (A2, C8, C10).
6. Resolve the T-017 one-server-vs-two decision (C4) and pin fixture provenance to a
   contracts commit (C9).
7. Split T-007 and T-011 along the framing/session and supervisor/reconciliation lines (C7).
8. Shape T-010 reducers and T-015 rows for the daily-UX shelf model (D2) and adopt the
   design tokens (D6) in the foundation shell.
