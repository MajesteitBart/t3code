# Pi

This guide is for using Pi Agent as a T3 Code provider.

## Setup

Install Pi so the `pi` command is available on the machine running T3 Code. For npm-based installs, this is typically:

```bash
npm install -g @earendil-works/pi-coding-agent
```

Open T3 Code Settings, enable the Pi provider, and leave `Binary path` as `pi` when the command is on `PATH`. If T3 Code runs in an environment that cannot resolve `pi`, set `Binary path` to the full Pi CLI path.

Pi model credentials and provider definitions remain owned by Pi. Custom providers and models configured in Pi, such as entries in `~/.pi/agent/models.json`, are discovered by T3 Code through Pi RPC.

## Models

T3 Code asks Pi for available models dynamically. Pi models use a `provider/model` slug, for example:

```text
baseten/zai-org/GLM-5.2
```

T3 Code does not provide a fake fallback Pi model. If Pi is enabled but model discovery fails and no custom Pi model is configured, the model picker shows an empty state and sending is disabled until a real Pi model is available.

## Thinking Options

Pi thinking controls are exposed through the normal model options UI. The supported levels are:

```text
off, minimal, low, medium, high
```

Codex-compatible Pi models may also expose `xhigh` when Pi reports that model family.

## Browser Support

T3 Code's collaborative in-app browser is exposed to providers through T3-owned MCP `preview_*` tools. Pi consumes that server through the Pi extension `pi-mcp-adapter`.

Install the extension in the same Pi environment T3 Code launches:

```bash
pi install npm:pi-mcp-adapter
```

When a Pi session starts and T3 has a provider-scoped preview MCP endpoint, T3 Code writes a scoped Pi MCP config, passes it through `--mcp-config`, and provides the bearer token through the process environment rather than persisting it to disk. The config exposes a `t3-code` MCP server with `toolPrefix: "none"`, so Pi can call preview tools directly when cached, or through the Pi MCP proxy on first use:

```js
mcp({ search: "preview" });
mcp({ tool: "preview_status", args: "{}" });
```

T3 Code also appends Pi system-prompt guidance that keeps browser work on the T3 preview MCP surface. It does not silently fall back to Pi `agent-browser`, global Chrome automation, standalone Playwright, or another external browser path.

## V1 Limitations

- Attachments are not supported for Pi turns yet. File mentions, browser element context, preview annotation text, and review comments are sent as prompt text, but image attachments are blocked before dispatch so the composer draft remains available for retry.
- Pi does not expose provider-native slash-command metadata yet. T3 Code keeps provider-command results empty for Pi until there is an executable Pi path, but `$skill` still uses the workspace skill catalog when available.
- Pi extension status UI updates are ignored; interactive extension UI requests are reported as unsupported warnings.
- Browser work through T3's preview MCP tools requires `pi-mcp-adapter` and an automation-capable T3 desktop preview owner for the scoped thread.
- Model selection must resolve to a real Pi `provider/model` slug before sending.
