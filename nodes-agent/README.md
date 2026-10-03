# Brick Nodes Agent

> Default agent-chain nodes and the default graph: a reference implementation of the chat agent loop.

- Coordinates: `tech.forethought.brick:nodes-agent:0.2.0`
- Nodes (`Node` implementations, declared via `META-INF/services`): `input`, `context-preprocess`, `convert-out`, `llm-call`, `convert-in`, `tool-exec`, `output`
- Default graph: `DefaultSpecs.chat()` — a pure five-node outer DAG (`config` → `input` → `preprocess` → `agent-loop` → `out`); the agent loop lives inside the `agent-loop` loop container whose embedded body wires `convert` → `call` → `convert-back` → `has-tools` (branch gateway) → (`tool-exec` back in, or straight through on `false`) → `pass`. Ships as version-2 `chat.json`; run injects `input.text` plus the conversation-so-far on `input.history` (empty list on the first turn) and reads the result from `out.output`
- Pin-name conventions live in `AgentKeys`; the model config travels as one `llm` MAP pin from a config file (loaded by config-loader) — graphs carry no credentials
- Positioning: the official default implementation, a starting point — every node can be replaced by a custom implementation

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; build `core` and `nodes-basic` first (`mvnd install` in each), then run inside this directory:

```bash
mvnd test   # includes the end-to-end acceptance of the default graph (mock adapter + mock tool, no network)
```

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
