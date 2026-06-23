import type { ProviderDriverKind } from "@t3tools/contracts";

import type { ComposerTrigger } from "../../composer-logic";

export interface ComposerSlashModelPickerAction {
  readonly provider: ProviderDriverKind;
  readonly query: string;
  readonly rangeStart: number;
  readonly rangeEnd: number;
  readonly expectedText: string;
}

export function resolveComposerSlashModelPickerAction(input: {
  readonly trigger: ComposerTrigger | null;
  readonly prompt: string;
  readonly selectedProvider: ProviderDriverKind;
}): ComposerSlashModelPickerAction | null {
  if (input.trigger?.kind !== "slash-model") {
    return null;
  }

  const safeStart = Math.max(0, Math.min(input.prompt.length, input.trigger.rangeStart));
  const safeEnd = Math.max(safeStart, Math.min(input.prompt.length, input.trigger.rangeEnd));
  return {
    provider: input.selectedProvider,
    query: input.trigger.query,
    rangeStart: safeStart,
    rangeEnd: safeEnd,
    expectedText: input.prompt.slice(safeStart, safeEnd),
  };
}
