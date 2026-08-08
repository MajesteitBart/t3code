# GUI Testing Policy

## Enforcement Mode

- Automated and build verification is required for user-visible client changes.
- Integrated browser, simulator, emulator, or computer-use verification requires explicit user approval before launching it.
- Use the repository `test-t3-app` skill for web and `test-t3-mobile` for the selected React Native or SwiftUI client. Standalone native Android currently uses the focused `apps/kotlin-android` Gradle install/instrumentation tasks plus an explicitly approved, isolated emulator; a unified mobile-skill path remains follow-on tooling work.

## Critical Routes and States

- Pairing/onboarding: valid, invalid, revoked, offline, local-network denial, QR/deep-link, and saved-environment restoration.
- Home: multi-environment rows, search, pinned/active/snoozed/settled/archived states, reachability, and phone/tablet navigation.
- New task: environment/project, Git/worktree/branch, provider/model/options, draft restoration, and send failure.
- Thread: long transcript, streaming Markdown, keyboard/bottom anchoring, attachments, approval, structured input, interrupt, retry, and process death.
- Workspace: files, diff/review, Git actions, terminal sessions, environment switching, and reconnect.
- Platform entry points: cold-start deep links, share targets, shortcuts, notifications, widgets, and background work.

## Blocking Runtime Evidence

- Unhandled exceptions, protocol decoding failures, reconnect loops, duplicate commands, lost pending messages, stuck loading/working indicators, stale environment state, terminal corruption, or persistent frame/memory regressions block completion.
- Expected transient connection errors are acceptable only when the UI recovers and the test records the recovery.

## Evidence Requirements

- Record the app variant, client, device/emulator, OS/API level, server connection mode, and exact scenario.
- Capture focused test/build output plus screenshots for visual changes and video for motion/timing changes.
- For process-death and reconnect cases, record the stable IDs and resulting server state proving no duplicate dispatch.

## Quality Bar

- Native Android should feel conventional on Android rather than mimic iOS controls literally.
- Phone and tablet layouts, predictive back, hardware/software keyboards, TalkBack, font scaling, light/dark themes, and reduced-motion behavior are part of the affected-surface decision.
- Large home lists, long transcripts, streaming Markdown, diffs, and terminal output must remain responsive under representative benchmark fixtures.
