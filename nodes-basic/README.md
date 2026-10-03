# Brick Nodes Basic

> First-party default node pack: basic nodes (gateways and producers).

- Coordinates: `tech.forethought.brick:nodes-basic:0.3.0-SNAPSHOT`
- Contents: control structures — `branch` (selective-delivery gateway: reads `control` + `value`, delivers `value` under the matching config `cases` pin or `default`; a BOOLEAN control without config degrades to the `true`/`false` two-pin form), `loop` (loop container: config embeds a complete body document — while-first semantics, carried values are the body inputs ∩ outputs by same key, output declarations may rename exposure with `as`; config `condition`, `maxIterations`), and `pass` (forwards `value` as-is — the join node for multi-source pins); plus `config-loader` (loads a `.properties` file into one MAP pin; config `path`, `as`).
- Nodes are declared via `META-INF/services` and discovered through `ServiceLoader`; no UI frameworks, no third-party runtime dependencies.

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; build `core` first (`cd ../core && mvnd install`), then run inside this directory:

```bash
mvnd test   # run tests (including inherited core contract suites and the agent-loop acceptance test)
```

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
