import type { ComposerTriggerKind } from "../../composer-logic";

export function resolveComposerMenuEmptyState(input: {
  readonly triggerKind: ComposerTriggerKind | null;
  readonly hasEnabledSkills: boolean;
}): string {
  if (input.triggerKind === "skill") {
    return input.hasEnabledSkills ? "No matching skills." : "No skills found.";
  }

  return input.triggerKind === "path" ? "No matching files or folders." : "No matching command.";
}
