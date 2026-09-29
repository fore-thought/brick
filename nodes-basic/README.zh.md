# Brick Nodes Basic

> 第一方默认节点包：基础节点（网关与取值）。

- 坐标：`tech.forethought.brick:nodes-basic:0.2.0-SNAPSHOT`
- 内容：`if`（比较输入键与期望值，路由 `true` / `false`）、`switch`（按输入键值的字符串形式路由）、`config-loader`（加载 `.properties` 文件并把条目放上边数据，配置 `path`）。
- 节点经 `META-INF/services` 声明，由 `ServiceLoader` 发现；不依赖任何 UI 框架，不含运行时第三方依赖。

## 构建

环境要求 JDK 25+ 与 mvnd；需先构建 `core`（`cd ../core && mvnd install`），然后在本目录内执行：

```bash
mvnd test   # 运行测试（含继承自 core 契约套件的用例与 agent loop 验收测试）
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
