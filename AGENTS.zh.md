# Brick（平台仓）项目规范

> 本仓是 brick 项目组的平台仓。项目组工程总规范与子项目索引见上级 `brick-group/AGENTS.zh.md`（brick-group 为项目组的公开规范仓，单独克隆本仓时请先获取它）；本文件未覆盖的事项一律以项目组规范为准。
>
> English version: [AGENTS.md](AGENTS.md).

## 项目定位

- 平台仓：纯库集合，不含可运行入口（无 `main`）；各模块可独立发布为 JAR。
- 将以 Maven 多模块组织；根 `pom.xml` 将改造为 parent pom（`packaging=pom`：聚合器 + 版本/继承管理，发布到 Maven Central 供实例项目与第三方插件继承）。具体目录名与模块划分尚未定稿。

## 环境与命令

环境要求：JDK 25+，mvnd（Maven Daemon）；测试框架 JUnit 6.1.3（test scope）。

- 编译：`mvnd compile`
- 运行测试：`mvnd test`
- 运行程序：`java -cp target/classes tech.forethought.brick.Main`（需先编译）

（以上命令作用于过渡期根模块；子模块落地后改为在各模块目录内执行。）

## 过渡期现状

仓库仅提交过 `LICENSE`；当前根目录的 `pom.xml`、`src/` 为 IDEA 脚手架兼临时模板，子模块落地后即移除，根 `pom.xml` 转为 parent pom。
