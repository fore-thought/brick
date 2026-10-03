# Brick Ext OpenAI

> OpenAI 协议适配器（`ProtocolAdapter` 实现），Brick 图引擎的首个真实协议扩展。

- 坐标：`tech.forethought.brick:ext-openai:0.3.0-SNAPSHOT`
- 协议名：`openai`（`ModelConfig.protocol` 引用此名）
- 实现要点：JDK 内置 `HttpClient` + 手写 SSE 流式解析 + 内置极简 JSON 编解码（仅覆盖协议所需形状）；零第三方依赖。
- 经 `META-INF/services` 声明，由 `ServiceLoader` 发现。
- 已知债务：`authToken` 随 `ProtocolRequest` 载荷在边上流动，观测线（M3）落地时需加敏感键脱敏。

## 构建

环境要求 JDK 25+ 与 mvnd；需先构建 `core`（`cd ../core && mvnd install`），然后在本目录内执行：

```bash
mvnd test   # 本地假 HTTP 服务上跑契约套件与适配器单测，不触网、不需要密钥
```

## 文档

- 项目规范：上级目录的 `AGENTS.md` / `AGENTS.zh.md`
- 项目组工程规范：brick-group 规范仓（单独克隆本仓时请先获取）
