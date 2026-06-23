# Project Style Guide

## Naming

- Delano project slugs use kebab-case.
- Workstream ids use `WS-A`, `WS-B`, and so on.
- Task ids use `T-001`, `T-002`, and so on.
- Provider driver kind for Pi should be the lowercase slug `pi` unless the probe discovers a stronger repo convention.
- Provider instance ids remain user-defined routing keys.

## Documentation Conventions

- Use UTC ISO timestamps in frontmatter.
- Record evidence in task `Evidence Log` sections and project `updates/`.
- Cite external issue or PR URLs as source references, but keep project state summarized in local contracts.
- State uncertainty explicitly instead of encoding guesses as requirements.

## Review Expectations

- For code completion, run `vp check` and `vp run typecheck`.
- Add targeted tests for any contract, adapter, provider snapshot, settings, or UI selection changes.
- For UI changes, run the app and verify provider picker, model picker, option controls, and chat rendering in a real browser.
- Keep `.repos/` read-only.
- Avoid unrelated refactors and preserve user changes in the worktree.
