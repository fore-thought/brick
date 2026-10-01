# Brick Nodes Basic

> First-party default node pack: basic nodes (gateways and producers).

- Coordinates: `tech.forethought.brick:nodes-basic:0.2.0-SNAPSHOT`
- Contents: `if` (selective-delivery gateway: reads `control` + `value`, delivers `value` under the `true` or `false` pin only), `switch` (selective-delivery gateway: reads `value`, delivers it under the matching config `cases` pin or `default`), and `config-loader` (loads a `.properties` file into one MAP pin; config `path`, `as`).
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
