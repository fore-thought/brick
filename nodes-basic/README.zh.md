# Brick Nodes Basic

> 第一方默认节点包：基础节点（网关与取值）。

- 坐标：`tech.forethought.brick:nodes-basic:0.2.0-SNAPSHOT`
- 内容：控制结构——`branch`（选择性投递网关：读 `control` + `value`，投到 config `cases` 中匹配的引脚或 `default`；BOOLEAN control 免 config 时退化为 `true`/`false` 两引脚）、`loop`（循环容器：config 内嵌一份完整 body 文档——while 先判后跑，携带值 = body inputs∩outputs 同名，输出声明可用 `as` 改名暴露；配置 `condition`、`maxIterations`）、`pass`（原样转发 `value`——多源引脚的汇合节点）；外加 `config-loader`（加载 `.properties` 文件写入一个 MAP 引脚，配置 `path`、`as`）。
- 节点经 `META-INF/services` 声明，由 `ServiceLoader` 发现；不依赖任何 UI 框架，不含运行时第三方依赖。

## 构建

环境要求 JDK 25+ 与 mvnd；需先构建 `core`（`cd ../core && mvnd install`），然后在本目录内执行：

```bash
mvnd test   # 运行测试（含继承自 core 契约套件的用例与 agent loop 验收测试）
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
