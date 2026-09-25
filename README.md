# Brick

> A desktop AI tool whose core idea is **extreme customizability**: a headless core plus hot-swappable JAR extensions.

This repository is the **platform repo** of the Brick project group: pure libraries with no runnable entry points; runnable instances (frontends) live in their own repositories. 中文版本见 [README.zh.md](README.zh.md)。

## Status

Early stage: the architectural direction is decided; code has not started yet. The current root `pom.xml` and `src/` are IDEA scaffolding and will be restructured into a Maven multi-module layout (the root pom becomes the parent pom).

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

- Project-group engineering specification and subproject index: the brick-group specification repo (`AGENTS.md`)
- Repo-specific agent rules: [AGENTS.md](AGENTS.md) / [AGENTS.zh.md](AGENTS.zh.md)
- All documents are English-canonical, each paired with a Chinese version (`foo.md` + `foo.zh.md`) in the same directory; on divergence the Chinese version is authoritative.

## License

[MIT License](LICENSE)
