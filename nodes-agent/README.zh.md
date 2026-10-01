# Brick Nodes Agent

> 默认 agent 链节点包与默认图：聊天 agent 回路的参考实现。

- 坐标：`tech.forethought.brick:nodes-agent:0.2.0-SNAPSHOT`
- 节点（`Node` 实现，`META-INF/services` 声明）：`input`、`context-preprocess`、`convert-out`、`llm-call`、`convert-in`、`tool-exec`、`output`
- 默认图：`DefaultSpecs.chat()`——九个节点按数据流连线：`config`（config-loader）填充 `llm` MAP 引脚；`input` → `preprocess` → `convert` → `call` → `convert-back` → `has-tools`（if 网关，选择性投递）；`true` 分支经 `tool-exec` 回环到 `convert`，`false` 分支终于 `out`。以 version 2 `chat.json` 发布；run 注入 `input.text`，从 `out.output` 读结果
- 引脚命名约定见 `AgentKeys`；模型配置经一个 `llm` MAP 引脚来自配置文件（由 config-loader 加载），图数据不含密钥
- 定位：官方默认实现，抛砖引玉——任何节点都可被自定义实现替换

## 构建

环境要求 JDK 25+ 与 mvnd；需先构建 `core` 与 `nodes-basic`（各自目录内 `mvnd install`），然后在本目录内执行：

```bash
mvnd test   # 含默认图的端到端验收（mock 适配器 + mock 工具，不触网）
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
