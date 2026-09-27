# Brick (Platform Repo) Project Specification

> This repository is the platform repo of the Brick project group. The project-group engineering specification and subproject index live in `brick-group/AGENTS.md` one level up (brick-group is the group's public specification repo — fetch it when working on a standalone clone of this repo). Anything not covered by this file defers to the project-group specification.
>
> 中文版本见 [AGENTS.zh.md](AGENTS.zh.md)；两版内容分歧时以中文版为准。

## Project Positioning

- Platform repo: a collection of pure libraries with no runnable entry points (no `main`); each module can be published as a JAR independently.
- Organized as Maven multi-module, with modules as top-level directories (currently only `core/`). The parent pom (`packaging=pom`: aggregator + version/inheritance management, published to Maven Central for instance projects and third-party plugins to inherit from) is deliberately deferred until the module set settles.

## Environment & Commands

Requirements: JDK 25+, mvnd (Maven Daemon); test framework JUnit 6.1.3 (test scope, version-managed via the `junit-bom` import).

- Compile: `mvnd compile`
- Run tests: `mvnd test`

Run the commands inside each module directory (e.g., `cd core && mvnd test`).

## Transitional Status Quo

The root IDEA scaffolding (former `pom.xml` and `src/`) has been removed. `core/` is the first module, currently a standalone pom (not yet inheriting from a parent). `TextTransformer` in `core` is a placeholder contract that only validates the SPI machinery (service declarations, ServiceLoader discovery, contract test suites); it will be replaced by real contracts when the engine lands.
