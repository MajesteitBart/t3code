import { describe, expect, it } from "vite-plus/test";

import {
  ProviderDriverKind,
  ProviderInstanceId,
  type ServerProvider,
  type ServerProviderSkill,
} from "@t3tools/contracts";

import { resolveComposerProviderSkills, searchProviderSkills } from "./providerSkillSearch";

function makeSkill(input: Partial<ServerProviderSkill> & Pick<ServerProviderSkill, "name">) {
  return {
    path: `/tmp/${input.name}/SKILL.md`,
    enabled: true,
    ...input,
  } satisfies ServerProviderSkill;
}

function makeProvider(
  input: Partial<ServerProvider> & Pick<ServerProvider, "driver" | "instanceId">,
): ServerProvider {
  return {
    displayName: String(input.driver),
    enabled: true,
    installed: true,
    version: "1.0.0",
    status: "ready",
    auth: { status: "authenticated" },
    checkedAt: "2026-06-23T00:00:00.000Z",
    models: [],
    slashCommands: [],
    skills: [],
    ...input,
  } as ServerProvider;
}

describe("searchProviderSkills", () => {
  it("moves exact ui matches ahead of broader ui matches", () => {
    const skills = [
      makeSkill({
        name: "agent-browser",
        displayName: "Agent Browser",
        shortDescription: "Browser automation CLI for AI agents",
      }),
      makeSkill({
        name: "building-native-ui",
        displayName: "Building Native Ui",
        shortDescription: "Complete guide for building beautiful apps with Expo Router",
      }),
      makeSkill({
        name: "ui",
        displayName: "Ui",
        shortDescription: "Explore, build, and refine UI.",
      }),
    ];

    expect(searchProviderSkills(skills, "ui").map((skill) => skill.name)).toEqual([
      "ui",
      "building-native-ui",
    ]);
  });

  it("uses fuzzy ranking for abbreviated queries", () => {
    const skills = [
      makeSkill({ name: "gh-fix-ci", displayName: "Gh Fix Ci" }),
      makeSkill({ name: "github", displayName: "Github" }),
      makeSkill({ name: "agent-browser", displayName: "Agent Browser" }),
    ];

    expect(searchProviderSkills(skills, "gfc").map((skill) => skill.name)).toEqual(["gh-fix-ci"]);
  });

  it("omits disabled skills from results", () => {
    const skills = [
      makeSkill({ name: "ui", displayName: "Ui", enabled: false }),
      makeSkill({ name: "frontend-design", displayName: "Frontend Design" }),
    ];

    expect(searchProviderSkills(skills, "ui").map((skill) => skill.name)).toEqual([]);
  });

  it("falls back to workspace skills when the selected provider has none", () => {
    const pi = makeProvider({
      driver: ProviderDriverKind.make("pi"),
      instanceId: ProviderInstanceId.make("pi"),
      skills: [],
    });
    const codex = makeProvider({
      driver: ProviderDriverKind.make("codex"),
      instanceId: ProviderInstanceId.make("codex"),
      skills: [
        makeSkill({ name: "review-follow-up", displayName: "Review Follow Up" }),
        makeSkill({ name: "disabled-skill", enabled: false }),
      ],
    });

    expect(
      resolveComposerProviderSkills({
        selectedProviderStatus: pi,
        providerStatuses: [pi, codex],
      }).map((skill) => skill.name),
    ).toEqual(["review-follow-up"]);
  });

  it("prefers selected provider skills when they exist", () => {
    const pi = makeProvider({
      driver: ProviderDriverKind.make("pi"),
      instanceId: ProviderInstanceId.make("pi"),
      skills: [makeSkill({ name: "pi-native" })],
    });
    const codex = makeProvider({
      driver: ProviderDriverKind.make("codex"),
      instanceId: ProviderInstanceId.make("codex"),
      skills: [makeSkill({ name: "codex-skill" })],
    });

    expect(
      resolveComposerProviderSkills({
        selectedProviderStatus: pi,
        providerStatuses: [pi, codex],
      }).map((skill) => skill.name),
    ).toEqual(["pi-native"]);
  });
});
