# Brick

> AI graph engine libraries whose core idea is **extreme customizability**: a headless graph-engine core plus hot-swappable JAR extensions.

This repository is the **platform repo** of the Brick project group: pure libraries with no runnable entry points; runnable instances (frontends) live in their own repositories. 中文版本见 [README.zh.md](README.zh.md)。

## Status

Early stage, actively developed: the graph-engine core, first-party node packs, an OpenAI adapter, and JSONL session persistence are in place. Latest tag: `v0.1.0`; `main` carries `0.2.0`. Modules are top-level directories (`core/`, `nodes-basic/`, `nodes-agent/`, `ext-openai/`, `store-jsonl/`); the root `pom.xml` is the parent pom.

## Principles

- **Everything is replaceable**: the platform defines only contracts (interfaces and data protocol formats); implementations are always options, and the choice happens at assembly time of an instance project.
- **The platform repo ships pure libraries**: runnable instances (frontends) live in their own repositories and assemble platform libraries as needed.
- **Lightweight and dependency-safe**: pure JDK implementations and zero third-party runtime dependencies by default.

## Build

Requires JDK 25+ and [mvnd](https://github.com/apache/maven-mvnd) (Maven Daemon).

```bash
mvnd compile   # compile
mvnd test      # run tests
```

## Documentation

- Canonical pipeline (graph topology) format: [doc/pipeline-format.md](doc/pipeline-format.md) / [doc/pipeline-format.zh.md](doc/pipeline-format.zh.md)
- Project-group engineering specification and subproject index: the brick-group specification repo (`AGENTS.md`)
- Repo-specific agent rules: [AGENTS.md](AGENTS.md) / [AGENTS.zh.md](AGENTS.zh.md)
- All documents are English-canonical, each paired with a Chinese version (`foo.md` + `foo.zh.md`) in the same directory; on divergence the Chinese version is authoritative.

## License

[MIT License](LICENSE)
