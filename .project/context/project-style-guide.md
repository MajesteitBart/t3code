# Project Style Guide

## Naming

- Use T3 glossary terms: environment, project, thread, turn, provider, client, and T3 home.
- Say **React Native mobile**, **SwiftUI mobile**, or **native Android** when implementation details matter; avoid ambiguous “mobile app.”
- Delano project slugs use lowercase kebab case; workstreams use `WS-A`, `WS-B`, and tasks use `T-001`, `T-002`.
- Use conventional, plain-language commit titles only when the user authorizes a commit.
- Kotlin packages and module names must follow the package/application identity decision recorded by the native Android project.

## Code and Architecture

- Prefer the smallest model that makes behavior obvious; do not preserve or introduce machinery for appearance.
- Keep orchestration pure, UI state simple, and complexity at adapter/platform boundaries.
- Prefer inferred TypeScript types and avoid `any`.
- Comments explain usage and invariants, especially public functions and platform bridges.
- Do not introduce continuously repainting animations or unbounded list/render caches.

## Documentation

- Write durable context as current facts, decisions, constraints, or explicit uncertainty.
- Use ISO dates for dated evidence and include the command/artifact that supports completion claims.
- Update user docs for shipped behavior, internal docs for architecture, and operations docs for runbooks.
- Do not duplicate long canonical docs into context; link to the owning file.

## Review Expectations

- One coherent concern per PR; no PR without explicit user request.
- Focused checks for changed behavior; backend changes include focused tests.
- UI changes need before/after evidence; timing/motion changes need video.
- Decide entry points, clients, providers, contracts, reverse states, connection modes, and docs explicitly.
- Native Android reviews must include performance, process-death, reconnect, accessibility, phone/tablet, and platform-entry-point implications where applicable.
