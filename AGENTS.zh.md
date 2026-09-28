# Brick（平台仓）项目规范

> 本仓是 brick 项目组的平台仓。项目组工程总规范与子项目索引见上级 `brick-group/AGENTS.zh.md`（brick-group 为项目组的公开规范仓，单独克隆本仓时请先获取它）；本文件未覆盖的事项一律以项目组规范为准。
>
> English version: [AGENTS.md](AGENTS.md).

## 项目定位

- 平台仓：纯库集合，不含可运行入口（无 `main`）；各模块可独立发布为 JAR。
- 以 Maven 多模块组织，模块位于仓内顶层目录（当前为 `core/`、`nodes-basic/`）。parent pom（`packaging=pom`：聚合器 + 版本/继承管理，发布到 Maven Central 供实例项目与第三方插件继承）有意搁置，待模块集合稳定后落地。

## 环境与命令

环境要求：JDK 25+，mvnd（Maven Daemon）；测试框架 JUnit 6.1.3（test scope，经 `junit-bom` 导入管理）。

- 编译：`mvnd compile`
- 运行测试：`mvnd test`

以上命令在各模块目录内执行（如 `cd core && mvnd test`）。

## 过渡期现状

- 根目录的 IDEA 脚手架（旧 `pom.xml` 与 `src/`）已移除。各模块当前是独立 pom（暂不继承 parent），parent pom 有意搁置，待模块集合稳定后落地。
- 因无聚合器，跨模块依赖需先在被依赖模块内执行 `mvnd install`（或从配置的 snapshot 仓解析）。
- `core` 的测试设施（契约套件与测试替身）以 test-jar 随模块发布，供扩展模块继承运行同一套契约测试。
