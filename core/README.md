# Brick Core

> Core contracts of the Brick AI graph engine libraries.

- Coordinates: `tech.forethought.brick:core:0.1.0-SNAPSHOT`
- Status: transitional — `TextTransformer` is a placeholder contract that only validates the SPI machinery (service declarations, ServiceLoader discovery, contract test suites); it will be replaced by real contracts when the engine lands.

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; run inside this directory:

```bash
mvnd compile   # compile
mvnd test      # run tests
```

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
