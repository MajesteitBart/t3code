---
id: T-002
name: Scaffold the standalone Compose application
status: planned
workstream: WS-A
created: 2026-08-07T13:16:53Z
updated: 2026-08-08T10:32:44Z
linear_issue_id:
github_issue:
github_pr:
depends_on: [T-001]
conflicts_with: [Gradle settings, root build files]
parallel: false
priority: high
estimate: L
operating_mode: multi-stream
story_id: US-001
acceptance_criteria_ids: [AC-001]
---

# Task: Scaffold the standalone Compose application

## Description

Create the approved Gradle application and minimal app, core-protocol, core-data, and core-testing boundaries with independent development and release-shaped variants.

## Acceptance Criteria

- [ ] A focused Gradle command assembles both development and release-shaped variants from a clean checkout.
- [ ] The development variant installs beside the React Native Android app without sharing application data.
- [ ] The app renders a minimal Compose root on the supported phone emulator.
- [ ] The manifest declares the approved custom pairing scheme and only the verified owned-host App Link filters decided in T-001; it does not claim arbitrary `http/https` server hosts.
- [ ] Target-SDK local-network permissions, rationale, and build-variant behavior match the T-001 decision without granting unrelated nearby-device capabilities.
- [ ] No Expo prebuild, Metro process, or apps/mobile generated Android directory is required.

## Traceability

- Story: US-001
- Acceptance criteria: AC-001

## Technical Notes

- Route parsing and UI handling remain T-006/T-014; this task owns only manifest/application plumbing.
- Keep development and release-shaped schemes/hosts collision-safe with React Native Android. Do not copy the SwiftUI-only `t3code-swiftui[-dev]` scheme.
- If owned-host Digital Asset Links cannot yet be published for the provisional identity, keep verified App Links disabled/documented rather than pretending verification succeeded; custom-scheme and paste paths still satisfy the foundation flow.

## Definition of Done

- [ ] Implementation complete
- [ ] Tests pass
- [ ] Review complete
- [ ] Docs updated

## Evidence Log

- 2026-08-07T13:16:53Z: Created from .project/templates/task.md by `delano task add`.
