# Brick Core

> Brick AI 图引擎库的核心模块：契约与引擎。

- 坐标：`tech.forethought.brick:core:0.2.0-SNAPSHOT`
- 内容：领域模型（`model`）、图数据结构（`spec`）、SPI 契约（`spi`：`Node` / `ProtocolAdapter` / `Tool`）、引擎（`engine`）、装配器（`bootstrap`）。
- 测试设施以 test-jar 随模块发布：SPI 契约测试套件与测试替身（`testkit` / `mock`），供扩展模块继承运行同一套契约测试，以证明可替换性。

## 构建

环境要求 JDK 25+ 与 mvnd；在本目录内执行：

```bash
mvnd compile   # 编译
mvnd test      # 运行测试
mvnd install   # 安装到本地仓（供同仓其他模块解析）
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
