# Brick (Platform Repo) Project Specification

> This repository is the platform repo of the Brick project group. The project-group engineering specification and subproject index live in `brick-group/AGENTS.md` one level up (brick-group is the group's public specification repo — fetch it when working on a standalone clone of this repo). Anything not covered by this file defers to the project-group specification.
>
> 中文版本见 [AGENTS.zh.md](AGENTS.zh.md)；两版内容分歧时以中文版为准。

## Project Positioning

- Platform repo: a collection of pure libraries with no runnable entry points (no `main`); each module can be published as a JAR independently.
- To be organized as Maven multi-module; the root `pom.xml` will be reshaped into the parent pom (`packaging=pom`: aggregator + version/inheritance management, published to Maven Central for instance projects and third-party plugins to inherit from). Concrete directory names and the module breakdown are not finalized yet.

## Environment & Commands

Requirements: JDK 25+, mvnd (Maven Daemon); test framework JUnit 6.1.3 (test scope).

- Compile: `mvnd compile`
- Run tests: `mvnd test`
- Run the program: `java -cp target/classes tech.forethought.brick.Main` (compile first)

(The commands above target the transitional root module; once submodules land, run them inside each module directory instead.)

## Transitional Status Quo

The repository has only ever committed `LICENSE`; the current root `pom.xml` and `src/` are IDEA scaffolding doubling as a temporary template. Once submodules land, `src/` will be removed and the root `pom.xml` will become the parent pom.
