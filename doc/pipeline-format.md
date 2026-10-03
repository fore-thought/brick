# Pipeline Format

> Pipeline topology in Brick is data. This document defines its canonical expression — a JSON format shared by the blueprint editor, server instances, and hand-written configuration alike.
>
> 中文版本见 [pipeline-format.zh.md](pipeline-format.zh.md)；两版内容分歧时以中文版为准。

## 1. Status and Rationale

- **The canonical format is JSON.** Rationale: it is isomorphic with the spec record model (`PipelineSpec`/`NodeSpec`/`EdgeSpec`/`PinRef`), a near 1:1 mapping; core holds a zero-dependency line and adopts no third-party parser such as a YAML library; the repository has precedent (store-jsonl event persistence, `MessageCodec`).
- Topology is authored primarily by machines (the blueprint editor), secondarily by hand. Alternative formats (YAML/TOML etc.) may exist as loaders in separate modules without changing the canonical status.
- Codec: core's `PipelineSpecCodec` (`read`/`write` for JSON text, `fromMap`/`toMap` for the map form).

## 2. Document Structure

One document expresses one graph, encoded in UTF-8. Top-level object fields:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `version` | integer | yes | — | format version, currently `2` |
| `name` | string | yes | — | graph name |
| `maxFirings` | integer | no | `50` | cap on total node firings per run (loop protection) |
| `inputs` | array | no | `[]` | pin objects: the run's external injection points |
| `outputs` | array | no | `[]` | pin objects: where callers read run results |
| `nodes` | array | yes | — | node objects |
| `edges` | array | no | `[]` | edge objects |

Node object:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `id` | string | yes | — | unique within the graph; edges and inputs/outputs reference it |
| `type` | string | yes | — | node type, resolved to a `Node` implementation at assembly |
| `config` | object | no | `{}` | node configuration (see §3) |

Pin object (element of `inputs`/`outputs`):

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `node` | string | yes | — | node id |
| `key` | string | yes | — | pin name (node-local naming) |

Edge object:

| Field | Type | Required | Default | Description |
|---|---|---|---|---|
| `from` | pin object | yes | — | source pin (an output pin of some node) |
| `to` | pin object | yes | — | target pin (an input pin of some node) |

**Omission and defaults**: writers produce the canonical form — fields at their default value are omitted (`maxFirings=50` unwritten, empty `inputs`/`outputs` unwritten, `config={}` unwritten), and field order is fixed (the table order above). Readers are lenient: an absent or explicitly null field takes its default, and field order is insignificant.

## 3. Config Value Domain

`config` is an open container, continuing "key names are the protocol": keys are defined by each node implementation's own contract (core neither constrains nor enumerates them); custom keys should carry a prefix to avoid collisions.

Values must stay within the JSON value domain: objects, arrays, strings, numbers, booleans, null. Code-side assemblies that place foreign objects (e.g. a `Duration`) fail on export.

Numbers normalize to `Long` (integral) / `Double` (floating-point) after a JSON text round trip — when comparing topology data, note that a code-assembled `Integer` does not equal a parsed-back `Long`. The map form (`toMap`/`fromMap`) does not normalize; values pass through as-is.

## 4. Versioning

`version` is required, currently `2`. Reading any other version fails fast (`unsupported format version`).

**Changelog**:
- `v1`: a 0.2.0 development-period draft (execution-line model: `entry` pointer, route-labeled edges, `maxIterations`), never released; retired in place when v2 landed, with no automatic migration.
- `v2`: the dataflow model. Edges are pin-to-pin binding objects (`from`/`to` are pin objects); `entry` is gone, `inputs`/`outputs` declare the run's injection and result pins; `maxIterations` is reborn as `maxFirings`.
- `v3` (current, model layer): loops containerized — the `loop` node (a body document embedded in config, while base semantics, same-key carrying, optional `as` exposure name on output declarations) replaces in-graph back-edge loops; `branch` unifies the retired if/switch; `NodeContract` converges to two components, `reads`/`writes` (both the context and the dynamic component are gone — pins are always explicit, enumerable from `contract(config)`); outer graphs and loop bodies are all DAGs. The on-disk format is unchanged (`version` stays `2`; `as` is read by the container node and tolerated — ignored — by the codec, so existing documents are unaffected).

The format evolves by advancing the version number; there is no automatic migration — migration tooling is deferred until real consumers need it.

## 5. Diagnostics Contract

Two layers, continuing the "diagnostics, not gatekeepers" philosophy:

- **Parse time**: format errors (malformed JSON, missing or mistyped fields, unknown version) fail fast, with a JSON-path location in the exception message, e.g. `invalid pipeline format at $.nodes[2]: field 'id' must be a string`. A file that cannot be read means there is no graph; there is nothing to diagnose.
- **Semantic time**: structurally valid but semantically suspect graphs go through `SpecValidator`. Structural problems (duplicate node ids, edge and inputs/outputs references to unknown nodes) are errors — the only pre-run gate; pin-level findings (unknown pins, type-family mismatches, multiple edges into one input pin, unsourced reads, unconsumed writes) are all warnings — snapshots and drafts can always be saved, graphs can always run.

## 6. Model Notes (Dataflow Model)

- **An edge is a binding**: it carries one typed named value from a source node's output pin into a target node's input pin (`m = A.getX()`; `B.setU(m)`). Execution order is derived from data dependencies; there is no separate execution line.
- **A pin is a node's named port**: `NodeContract` reads/writes are real port declarations; pin names are node-local, and wiring translates between them (A's `x` may feed B's `u`).
- **Triggering (sticky bindings)**: once a value lands on a pin it stays there (later writes overwrite earlier ones); a node fires once all pins its contract declares as reads carry a binding, and re-fires when any read receives a new value. A node with no reads (a pure source) fires once at the start. The outer graph and every loop body are DAGs — each node fires at most once per round under this one strict rule; there is no hot/cold pin distinction.
- **Selective delivery (conditions = the chosen key)**: a `branch` node (control-structure family) returns an output map containing only the chosen branch's key; the engine delivers per key along out-edges, so pins of the untaken branch never receive a value and their subgraph never fires. A BOOLEAN control needs no config (two pins, `true`/`false`); otherwise config `cases` are matched and everything else falls to `default`.
- **Multiple in-edges (join)**: one input pin may have many in-edges: every arrival delivers, later writes overwrite. Joins after in-body branches use the trivial `pass` node (forwards as-is) to merge onto one deterministic output wire.
- **Same value is not re-delivered**: a redelivery equal (`equals`) to the pin's current value does not count as new and does not trigger downstream — idempotent writes cannot spin up loops.
- **Loops = the loop container node**: repetition leaves the graph. A `loop` node (nodes-basic) embeds a complete body document in its config (this very format, with inputs/outputs declarations): `{"body": {…}, "condition": {"pin": <body output key>, "initial": <value>}, "maxIterations": 50}`. Base semantics are while (condition first): read the condition's carried value (the `initial` on the first round, the body's same-named output afterwards); false → the carried values' finals are written to the container's output pins; true → run the body once through a nested engine (recursion; events are forwarded onto the outer observation line), the body's declared outputs update the carried values, back to the condition. **Carried values = the body's inputs ∩ outputs (same key)**: inputs become the container's input pins (injected on the first round, re-injected every round), outputs its output pins (final values); same-named ones carry across iterations, the condition pin among them (recomputed by the body every round). An output declaration may carry an optional `as` field to expose itself under another name (when the body's producer pin is named differently, e.g. `{"node": "pass", "key": "value", "as": "messages"}`). Inputs the body never re-produces (configuration) simply persist their injected value across rounds. **Zero-iteration outputs**: when the body never runs, container outputs take the carried values' initial values; a carried output with neither initial nor injection is omitted (validator warning, saving never blocked). Hitting the iteration cap throws (loop protection). When the condition pin is not among the body's outputs the body can never flip the condition: a truthy initial certainly spins to `maxIterations`, a falsy one certainly never runs — either way a `warning` event is emitted at execution (diagnostics, not gatekeepers) and the run continues.
- **Start and end**: the format has no "start node" / "end node" concepts. Starting points are nodes whose inputs become complete (including run injection); a run terminates silently when no delivery can trigger anything, and the `run-end` event reports the firing count and the nodes that never fired.
- **Fork and join**: independent branches fire in turn (parallel execution awaits engine support; the semantics already allow it); multiple in-edges are the join.
- **Subgraphs**: a subgraph is an implementation detail of an ordinary node (a node internally executing a nested spec); the format is unaware of it. Its external endpoints are simply that node's pins on the parent graph; since `config` is an open object, a whole document of this very format can be embedded as a subgraph. Referencing an external graph by name requires a graph library, a non-goal (§8).

## 7. Examples

Minimal runnable form (run injects `in.text`, reads the result from `out.output`):

```json
{
  "version": 2,
  "name": "minimal",
  "inputs": [{ "node": "in", "key": "text" }],
  "outputs": [{ "node": "out", "key": "output" }],
  "nodes": [
    { "id": "in", "type": "input" },
    { "id": "out", "type": "output" }
  ],
  "edges": [
    { "from": { "node": "in", "key": "messages" },
      "to":   { "node": "out", "key": "messages" } }
  ]
}
```

For a complete real-world example, see the default chat chain bundled with nodes-agent: [`nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json`](../nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json) — the default chain itself ships in this format and is loaded at runtime through `PipelineSpecCodec`.

## 8. Non-goals (explicitly out of scope for now)

- Multi-graph documents / graph libraries: deferred until server instances have real need.
- Automatic migration tooling: see §4.
- YAML/TOML loaders in core: alternative formats belong to separate modules.
- Pretty-printed output: writers currently emit compact single-line JSON; pretty output will be decided when the blueprint editor's on-disk needs arise.
