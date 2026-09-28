# Brick (Platform Repo) Project Specification

> This repository is the platform repo of the Brick project group. The project-group engineering specification and subproject index live in `brick-group/AGENTS.md` one level up (brick-group is the group's public specification repo — fetch it when working on a standalone clone of this repo). Anything not covered by this file defers to the project-group specification.
>
> 中文版本见 [AGENTS.zh.md](AGENTS.zh.md)；两版内容分歧时以中文版为准。

## Project Positioning

- Platform repo: a collection of pure libraries with no runnable entry points (no `main`); each module can be published as a JAR independently.
- Organized as Maven multi-module, with modules as top-level directories (currently `core/`, `nodes-basic/`, `ext-openai/`, `nodes-agent/`, `store-jsonl/`). The root `pom.xml` is the parent pom (`packaging=pom`: aggregator + version/inheritance management, to be published to Maven Central for instance projects and third-party plugins to inherit from).

## Environment & Commands

Requirements: JDK 25+, mvnd (Maven Daemon); test framework JUnit 6.1.3 (test scope, version-managed via the `junit-bom` import).

- Compile: `mvnd compile`
- Run tests: `mvnd test`

Run the commands at the repo root (the reactor builds all modules in dependency order); running them inside a single module directory works too.

## Status Quo

- The parent pom landed on 2026-09-28 (with five modules): cross-module builds are reactor-ordered, no more per-module `install`. Standalone consumers (frontend repos, etc.) still resolve platform modules via local `mvnd install` or a `settings.xml` snapshot repository.
- `core` ships its test fixtures (contract test suites and test doubles) as a test-jar, so extension modules can run the same contract suites.
