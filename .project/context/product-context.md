# Product Context

## Users

- Developers who run Codex, Claude, Cursor, Grok, or OpenCode and supervise agent work throughout the day.
- Users connecting from another device over a local network, Tailscale, a T3 relay, or a tunnel.
- Maintainers who need one server contract to remain coherent across web, desktop, React Native mobile, SwiftUI mobile, and the planned native Android client.
- Fork maintainers who rely on the project remaining open and understandable.

## Core Flows

- Pair with or authenticate to one or more environments and understand reachability.
- Browse projects and threads across environments without identity collisions.
- Create a task with the correct project, branch/worktree, provider, model, and options.
- Follow long-running chat output, send or recover messages, attach files, answer approvals and structured input, and interrupt work.
- Inspect files and diffs, perform source-control actions, and use terminal sessions on the server-owned workspace.
- Resume from reconnects, app restarts, network ambiguity, deep links, notifications, sharing, widgets, or shortcuts without duplicate commands or stale state.

## Product Constraints

- Performance is a product feature: large lists, long transcripts, streaming Markdown, terminals, and high-refresh displays must remain responsive without continuous repaint loops.
- Remote readiness is mandatory. Local, relay, tunnel, multi-device, and multi-environment cases cannot be treated as afterthoughts.
- The server is the execution boundary. Clients do not run provider, filesystem, Git, or terminal operations locally.
- Native clients may use platform conventions, but user-visible state and server behavior must agree across applicable surfaces.
- Actions need reverse states and visible truth: archive/restore, settle/reopen, snooze/unsnooze, connect/disconnect, and register/revoke.
- Security boundaries matter most around pairing credentials, OAuth tokens, DPoP keys, push tokens, signed asset URLs, and logs; do not expose secret values in delivery evidence.

## Known Product Uncertainty

- The root `README.md` describes released mobile distribution while `apps/mobile/README.md` still says the React Native client is not distributed. Treat the root documentation and current release configuration as newer evidence, but resolve the documentation drift before editing release instructions.
- Android equivalents for Live Activities and WidgetKit should preserve the user outcome through native Android patterns, not copy iOS interaction shapes mechanically.
