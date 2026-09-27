# Brick（平台仓）项目规范

> 本仓是 brick 项目组的平台仓。项目组工程总规范与子项目索引见上级 `brick-group/AGENTS.zh.md`（brick-group 为项目组的公开规范仓，单独克隆本仓时请先获取它）；本文件未覆盖的事项一律以项目组规范为准。
>
> English version: [AGENTS.md](AGENTS.md).

## 项目定位

- 平台仓：纯库集合，不含可运行入口（无 `main`）；各模块可独立发布为 JAR。
- 以 Maven 多模块组织，模块位于仓内顶层目录（当前仅 `core/`）。parent pom（`packaging=pom`：聚合器 + 版本/继承管理，发布到 Maven Central 供实例项目与第三方插件继承）有意搁置，待模块集合稳定后落地。

## 环境与命令

环境要求：JDK 25+，mvnd（Maven Daemon）；测试框架 JUnit 6.1.3（test scope，经 `junit-bom` 导入管理）。

- 编译：`mvnd compile`
- 运行测试：`mvnd test`

以上命令在各模块目录内执行（如 `cd core && mvnd test`）。

## 过渡期现状

根目录的 IDEA 脚手架（旧 `pom.xml` 与 `src/`）已移除。`core/` 为首个模块，当前是独立 pom（暂不继承 parent）。`core` 中的 `TextTransformer` 是占位契约，仅用于验证 SPI 机制（服务声明、ServiceLoader 发现、契约测试套件），引擎落地时将被真实契约替换。
