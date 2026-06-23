import { ProviderDriverKind, type ServerProvider } from "@t3tools/contracts";
import { describe, expect, it } from "vite-plus/test";

import { resolveComposerMenuEmptyState } from "./composerMenuEmptyState";

function makeProvider(overrides: Partial<ServerProvider> = {}): ServerProvider {
  return {
    instanceId: "pi",
    driver: ProviderDriverKind.make("pi"),
    displayName: "Pi",
    enabled: true,
    installed: true,
    version: "0.79.10",
    status: "ready",
    auth: { status: "authenticated" },
    checkedAt: "2026-06-23T00:00:00.000Z",
    models: [],
    slashCommands: [],
    skills: [],
    ...overrides,
  } as ServerProvider;
}

describe("resolveComposerMenuEmptyState", () => {
  it("marks providers with no executable skills as explicitly unsupported", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "skill",
        selectedProvider: ProviderDriverKind.make("pi"),
        selectedProviderStatus: makeProvider(),
      }),
    ).toBe("Pi does not expose executable skills yet.");
  });

  it("treats disabled skills as non-executable", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "skill",
        selectedProvider: ProviderDriverKind.make("pi"),
        selectedProviderStatus: makeProvider({
          skills: [
            {
              name: "review",
              path: "/tmp/review/SKILL.md",
              enabled: false,
            },
          ],
        }),
      }),
    ).toBe("Pi does not expose executable skills yet.");
  });

  it("uses a normal no-match state when executable skills exist", () => {
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "skill",
        selectedProvider: ProviderDriverKind.make("pi"),
        selectedProviderStatus: makeProvider({
          skills: [
            {
              name: "review",
              path: "/tmp/review/SKILL.md",
              enabled: true,
            },
          ],
        }),
      }),
    ).toBe("No matching skills.");
  });

  it("keeps path and slash command empty states unchanged", () => {
    const selectedProvider = ProviderDriverKind.make("pi");
    const selectedProviderStatus = makeProvider();

    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "path",
        selectedProvider,
        selectedProviderStatus,
      }),
    ).toBe("No matching files or folders.");
    expect(
      resolveComposerMenuEmptyState({
        triggerKind: "slash-command",
        selectedProvider,
        selectedProviderStatus,
      }),
    ).toBe("No matching command.");
  });
});
