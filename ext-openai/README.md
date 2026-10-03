# Brick Ext OpenAI

> OpenAI protocol adapter (a `ProtocolAdapter` implementation) — the first real protocol extension of the Brick graph engine.

- Coordinates: `tech.forethought.brick:ext-openai:0.2.0`
- Protocol name: `openai` (referenced by `ModelConfig.protocol`)
- Implementation: JDK `HttpClient` + hand-rolled SSE stream parsing + a built-in minimal JSON codec (covers exactly the protocol's shapes); zero third-party dependencies.
- Declared via `META-INF/services`, discovered through `ServiceLoader`.
- Known debt: `authToken` travels in the `ProtocolRequest` payload on edges; the observation line (M3) must add redaction for sensitive keys.

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; build `core` first (`cd ../core && mvnd install`), then run inside this directory:

```bash
mvnd test   # contract suite and adapter tests against a local fake HTTP server; no network, no credentials
```

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
