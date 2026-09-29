# Brick Store JSONL

> JSONL 会话存储：每会话一个 append-only 文件（`EventListener` 写侧 + `StateStore` 读侧）。

- 坐标：`tech.forethought.brick:store-jsonl:0.1.0`
- 写侧：durable 事件追加为 JSON 行；瞬态事件（`token-delta`）永不落盘；持久化失败不影响运行
- 读侧：`loadSession` 重放 `message-appended` 事件还原会话
- 零第三方依赖（JSON 编解码用 core 的内置极简 codec）
- 装配方式：编程式（目录是配置），不经 ServiceLoader

## 构建

环境要求 JDK 25+ 与 mvnd；需先构建 `core`（`cd ../core && mvnd install`），然后在本目录内执行 `mvnd test`。

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
