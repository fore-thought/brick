# Brick（平台仓）项目规范

> 本仓是 brick 项目组的平台仓。项目组工程总规范与子项目索引见上级 `brick-group/AGENTS.zh.md`（brick-group 为项目组的公开规范仓，单独克隆本仓时请先获取它）；本文件未覆盖的事项一律以项目组规范为准。
>
> English version: [AGENTS.md](AGENTS.md).

## 项目定位

- 平台仓：纯库集合，不含可运行入口（无 `main`）；各模块可独立发布为 JAR。
- 以 Maven 多模块组织，模块位于仓内顶层目录（当前为 `core/`、`nodes-basic/`、`ext-openai/`、`nodes-agent/`、`store-jsonl/`）。根 `pom.xml` 为 parent pom（`packaging=pom`：聚合器 + 版本/继承管理，将来发布到 Maven Central 供实例项目与第三方插件继承）。

## 环境与命令

环境要求：JDK 25+，mvnd（Maven Daemon）；测试框架 JUnit 6.1.3（test scope，经 `junit-bom` 导入管理）。

- 编译：`mvnd compile`
- 运行测试：`mvnd test`

以上命令在仓根目录执行即可（反应堆自动按依赖排序构建全部模块）；也可在单个模块目录内执行。

## 现状

- parent pom 已落地（2026-09-28，模块达五个时）：跨模块构建由反应堆自动排序，无需再逐模块 `install`；对外（前端仓等独立仓）解析平台模块仍需本地 `mvnd install` 或 settings.xml 快照仓。
- `core` 的测试设施（契约套件与测试替身）以 test-jar 随模块发布，供扩展模块继承运行同一套契约测试。
