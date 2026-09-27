# Brick Core

> Brick AI 图引擎库的核心契约模块。

- 坐标：`tech.forethought.brick:core:0.1.0-SNAPSHOT`
- 当前状态：过渡期——`TextTransformer` 为占位契约，仅验证 SPI 机制（服务声明、ServiceLoader 发现、契约测试套件），引擎落地时将被真实契约替换。

## 构建

环境要求 JDK 25+ 与 mvnd；在本目录内执行：

```bash
mvnd compile   # 编译
mvnd test      # 运行测试
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
