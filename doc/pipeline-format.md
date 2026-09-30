# Pipeline Format

> Pipeline topology in Brick is data. This document defines its canonical expression — a JSON format shared by the blueprint editor, server instances, and hand-written configuration alike.
>
> 中文版本见 [pipeline-format.zh.md](pipeline-format.zh.md)；两版内容分歧时以中文版为准。

## 1. Status and Rationale

- **The canonical format is JSON.** Rationale: it is isomorphic with the spec record model (`PipelineSpec`/`NodeSpec`/`EdgeSpec`), a near 1:1 mapping; core holds a zero-dependency line and adopts no third-party parser such as a YAML library; the repository has precedent (store-jsonl event persistence, `MessageCodec`).
- Topology is authored primarily by machines (the blueprint editor), secondarily by hand. Alternative formats (YAML/TOML etc.) may exist as loaders in separate modules without changing the canonical status.
- Codec: core's `PipelineSpecCodec` (`read`/`write` for JSON text, `fromMap`/`toMap` for the map form).

## 2. Document Structure

One document expresses one graph, encoded in UTF-8. Top-level object fields:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `version` | integer | yes | — | format version, currently `1` |
| `name` | string | yes | — | graph name |
| `entry` | string | yes | — | entry node id |
| `maxIterations` | integer | no | `50` | cap on node executions per run (loop protection) |
| `nodes` | array | yes | — | node objects |
| `edges` | array | no | `[]` | edge objects |

Node object:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string | yes | — | unique within the graph; edges reference it |
| `type` | string | yes | — | node type, resolved to a `Node` implementation at assembly |
| `config` | object | no | `{}` | node configuration (see §3) |

Edge object:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `from` | string | yes | — | source node id |
| `to` | string | yes | — | target node id |
| `label` | string | no | `null` | route label; absent means the default edge |

**Omission and defaults**: writers produce the canonical form — fields at their default value are omitted (`maxIterations=50` unwritten, `config={}` unwritten, `label=null` unwritten), and field order is fixed (the table order above). Readers are lenient: an absent or explicitly null field takes its default, and field order is insignificant.

## 3. Config Value Domain

`config` is an open container, continuing "key names are the protocol": keys are defined by each node implementation's own contract (core neither constrains nor enumerates them); custom keys should carry a prefix to avoid collisions.

Values must stay within the JSON value domain: objects, arrays, strings, numbers, booleans, null. Code-side assemblies that place foreign objects (e.g. a `Duration`) fail on export.

Numbers normalize to `Long` (integral) / `Double` (floating-point) after a JSON text round trip — when comparing topology data, note that a code-assembled `Integer` does not equal a parsed-back `Long`. The map form (`toMap`/`fromMap`) does not normalize; values pass through as-is.

## 4. Versioning

`version` is required, currently `1`. Reading an unknown version fails fast. The format evolves by advancing the version number; there is no automatic migration — migration tooling is deferred until real consumers need it.

## 5. Diagnostics Contract

Two layers, continuing the "diagnostics, not gatekeepers" philosophy:

- **Parse time**: format errors (malformed JSON, missing or mistyped fields, unknown version) fail fast, with a JSON-path location in the exception message, e.g. `invalid pipeline format at $.nodes[2]: field 'id' must be a string`. A file that cannot be read means there is no graph; there is nothing to diagnose.
- **Semantic time**: structurally valid but semantically suspect graphs (dangling references, unreachable nodes, etc.) go through `SpecValidator`, which produces a diagnostic list (severity + location + message); the caller decides what to block on — snapshots and drafts can always be saved.

## 6. Reserved Expressiveness and Model Notes

- **Start and end**: the format has no "start node" / "end node" concepts. The entry is the `entry` pointer; a node with zero out-edges is a terminal, where the run ends naturally. If an editor wants visual anchors, it derives them at the editor layer or a node pack provides conventional types — none of the format's business.
- **Single edge kind**: there is only one kind of edge — the execution flow (optionally carrying a route label). Data travels on no separate wires: run data is an open container passed station to station (immutable snapshots, key names as the protocol). The keys a node reads and writes are documented conventions of its contract, not typed ports at the format layer.
- **Fork and join**: one `from` may have many out-edges and one `to` many in-edges — parallel-branch topology is already expressible, so future parallel execution needs no syntax change. Execution semantics (currently label routing plus the default edge) are defined by the engine, decoupled from the document format.
- **Subgraphs**: a subgraph is an implementation detail of an ordinary node (a node internally executing a nested spec); the format is unaware of it. Its external interface is simply that node's key contract and `config`; since `config` is an open object, a whole document of this very format can be embedded as a subgraph — no multi-graph document needed. Referencing an external graph by name requires a graph library, a non-goal (§8).

## 7. Examples

Minimal runnable form:

```json
{
  "version": 1,
  "name": "minimal",
  "entry": "in",
  "nodes": [
    { "id": "in", "type": "input" },
    { "id": "out", "type": "output" }
  ],
  "edges": [
    { "from": "in", "to": "out" }
  ]
}
```

An edge with a route label (a gateway node's branch):

```json
{ "from": "has-tools", "to": "exec", "label": "true" }
```

For a complete real-world example, see the default chat chain bundled with nodes-agent: [`nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json`](../nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json) — the default chain itself ships in this format and is loaded at runtime through `PipelineSpecCodec`.

## 8. Non-goals (explicitly out of scope for now)

- Multi-graph documents / graph libraries: deferred until server instances have real need.
- Automatic migration tooling: see §4.
- YAML/TOML loaders in core: alternative formats belong to separate modules.
- Pretty-printed output: writers currently emit compact single-line JSON; pretty output will be decided when the blueprint editor's on-disk needs arise.
