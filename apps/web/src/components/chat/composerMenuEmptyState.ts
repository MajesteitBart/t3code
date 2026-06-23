import type { ProviderDriverKind, ServerProvider } from "@t3tools/contracts";

import type { ComposerTriggerKind } from "../../composer-logic";

function providerDisplayName(input: {
  readonly selectedProvider: ProviderDriverKind;
  readonly selectedProviderStatus: ServerProvider | null | undefined;
}): string {
  return input.selectedProviderStatus?.displayName?.trim() || String(input.selectedProvider);
}

function hasExecutableSkills(provider: ServerProvider | null | undefined): boolean {
  return (provider?.skills ?? []).some((skill) => skill.enabled);
}

export function resolveComposerMenuEmptyState(input: {
  readonly triggerKind: ComposerTriggerKind | null;
  readonly selectedProvider: ProviderDriverKind;
  readonly selectedProviderStatus: ServerProvider | null | undefined;
}): string {
  if (input.triggerKind === "skill") {
    if (!hasExecutableSkills(input.selectedProviderStatus)) {
      return `${providerDisplayName(input)} does not expose executable skills yet.`;
    }
    return "No matching skills.";
  }

  return input.triggerKind === "path" ? "No matching files or folders." : "No matching command.";
}
