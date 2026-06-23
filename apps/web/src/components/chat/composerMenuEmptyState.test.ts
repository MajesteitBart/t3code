import { describe, expect, it } from "vite-plus/test";

import { resolveComposerMenuEmptyState } from "./composerMenuEmptyState";

describe("resolveComposerMenuEmptyState", () => {
  it("uses an empty skills state when no composer skills are available", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "skill",
        hasEnabledSkills: false,
      }),
    ).toBe("No skills found.");
  });

  it("uses a normal no-match state when composer skills exist", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "skill",
        hasEnabledSkills: true,
      }),
    ).toBe("No matching skills.");
  });

  it("keeps path and slash command empty states unchanged", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "path",
        hasEnabledSkills: false,
      }),
    ).toBe("No matching files or folders.");
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "slash-command",
        hasEnabledSkills: false,
      }),
    ).toBe("No matching command.");
  });
});
