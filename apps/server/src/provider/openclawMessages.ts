function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function trimOrNull(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function appendTextPart(target: Array<string>, value: unknown): void {
  const text = trimOrNull(value);
  if (text) {
    target.push(text);
  }
}

export function getOpenClawMessageRole(value: unknown): string | null {
  return trimOrNull(asRecord(value)?.role);
}

export function getOpenClawMessageRunId(value: unknown): string | null {
  const message = asRecord(value);
  return trimOrNull(message?.runId) ?? trimOrNull(message?.idempotencyKey);
}

export function getOpenClawMessageId(value: unknown): string | null {
  const message = asRecord(value);
  return trimOrNull(message?.id) ?? trimOrNull(message?.messageId);
}

export function getOpenClawMessageText(value: unknown): string {
  const message = asRecord(value);
  if (!message) {
    return "";
  }

  const parts: Array<string> = [];
  appendTextPart(parts, message.text);

  const content = Array.isArray(message.content) ? message.content : [];
  for (const entry of content) {
    if (typeof entry === "string") {
      appendTextPart(parts, entry);
      continue;
    }

    const part = asRecord(entry);
    if (!part) {
      continue;
    }

    appendTextPart(parts, part.text);
    appendTextPart(parts, part.output);
  }

  return parts.join("\n").trim();
}
