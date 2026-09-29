# Brick Core

> Core module of the Brick AI graph engine libraries: contracts and engine.

- Coordinates: `tech.forethought.brick:core:0.2.0-SNAPSHOT`
- Contents: domain data (`model`), graph structure (`spec`), SPI contracts (`spi`: `Node` / `ProtocolAdapter` / `Tool`), the engine (`engine`), and assembly (`bootstrap`).
- Test fixtures ship as a test-jar: SPI contract test suites and test doubles (`testkit` / `mock`), so extension modules can run the same suites against their implementations to prove replaceability.

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; run inside this directory:

```bash
mvnd compile   # compile
mvnd test      # run tests
mvnd install   # install to the local repo (so sibling modules can resolve it)
```

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
