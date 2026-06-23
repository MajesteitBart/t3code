import { ProviderDriverKind } from "@t3tools/contracts";
import { describe, expect, it } from "vite-plus/test";

import { detectComposerTrigger } from "../../composer-logic";
import { resolveComposerSlashModelPickerAction } from "./composerSlashModelTrigger";

describe("resolveComposerSlashModelPickerAction", () => {
  it("routes Pi /model query text to the local model picker action", () => {
    const prompt = "/model glm";
    const trigger = detectComposerTrigger(prompt, prompt.length);

    expect(
      resolveComposerSlashModelPickerAction({
        trigger,
        prompt,
        selectedProvider: ProviderDriverKind.make("pi"),
      }),
    ).toEqual({
      provider: ProviderDriverKind.make("pi"),
      query: "glm",
      rangeStart: 0,
      rangeEnd: prompt.length,
      expectedText: prompt,
    });
  });

  it("keeps the same /model query behavior for non-Pi providers", () => {
    const prompt = "/model opus";
    const trigger = detectComposerTrigger(prompt, prompt.length);

    expect(
      resolveComposerSlashModelPickerAction({
        trigger,
        prompt,
        selectedProvider: ProviderDriverKind.make("codex"),
      }),
    ).toEqual({
      provider: ProviderDriverKind.make("codex"),
      query: "opus",
      rangeStart: 0,
      rangeEnd: prompt.length,
      expectedText: prompt,
    });
  });

  it("ignores normal slash commands", () => {
    const prompt = "/plan";

    expect(
      resolveComposerSlashModelPickerAction({
        trigger: detectComposerTrigger(prompt, prompt.length),
        prompt,
        selectedProvider: ProviderDriverKind.make("pi"),
      }),
    ).toBeNull();
  });
});
