# Brick Store JSONL

> JSONL session store: one append-only file per session (write side `EventListener`, read side `StateStore`).

- Coordinates: `tech.forethought.brick:store-jsonl:0.1.0-SNAPSHOT`
- Write side: durable events appended as JSON lines; transient events (`token-delta`) are never persisted; persistence failures never break a run
- Read side: `loadSession` replays `message-appended` events to reconstruct the conversation
- Zero third-party dependencies (JSON via core's built-in minimal codec)
- Assembled programmatically (the directory is configuration), not via ServiceLoader

中文版本见 [README.zh.md](README.zh.md)。

## Build

Requires JDK 25+ and mvnd; build `core` first (`cd ../core && mvnd install`), then run `mvnd test` inside this directory.

## Documentation

- Project specification: `AGENTS.md` / `AGENTS.zh.md` in the parent directory
- Project-group engineering specification: the brick-group specification repo (fetch it when working on a standalone clone)
