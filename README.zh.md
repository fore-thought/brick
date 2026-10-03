# Brick

> 一套 AI 图引擎库，核心理念是**高度可定制**：无头图引擎内核 + 热插拔 JAR 扩展。

本仓是 brick 项目组的**平台仓**：纯库集合，不含可运行入口；可运行实例（前端）位于各自独立的仓库。English version: [README.md](README.md).

## 状态

早期阶段，活跃开发中：图引擎内核、第一方节点包、OpenAI 适配器、JSONL 会话持久化已就位。最新 tag：`v0.1.0`；`main` 为 `0.2.0`。模块为顶层目录（`core/`、`nodes-basic/`、`nodes-agent/`、`ext-openai/`、`store-jsonl/`）；根 `pom.xml` 为父 pom。

## 设计理念

- **万物皆可替换**：平台只定义契约（接口与数据协议格式），实现一律可选，选择权发生在实例项目的组装时刻。
- **平台仓纯库化**：可运行实例（前端）放在独立仓库，按需装配平台的库。
- **轻量与依赖安全**：默认纯 JDK 实现、零第三方运行时依赖。

## 构建

需要 JDK 25+ 与 [mvnd](https://github.com/apache/maven-mvnd)（Maven Daemon）。

```bash
mvnd compile   # 编译
mvnd test      # 运行测试
```

## 文档

- 图拓扑 canonical 格式：[doc/pipeline-format.md](doc/pipeline-format.md) / [doc/pipeline-format.zh.md](doc/pipeline-format.zh.md)
- 项目组工程总规范与子项目索引：brick-group 规范仓（`AGENTS.md`）
- 本仓特定规则：[AGENTS.md](AGENTS.md) / [AGENTS.zh.md](AGENTS.zh.md)
- 所有文档以英文为正本，同目录配对中文版（`foo.md` + `foo.zh.md`）；两版分歧时以中文版为准。

## 许可

[MIT License](LICENSE)
