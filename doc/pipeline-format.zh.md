# 图拓扑格式（Pipeline Format）

> Brick 的图拓扑是数据。本文档定义拓扑的 canonical 表达——JSON 格式。蓝图编辑器、server 实例、手写配置共用这一种格式。
>
> English version: [pipeline-format.md](pipeline-format.md)；两版内容分歧时以中文版为准。

## 1. 地位与选型

- **canonical 格式 = JSON**。理由：与 spec 记录模型（`PipelineSpec`/`NodeSpec`/`EdgeSpec`）同构，映射近乎 1:1；core 守零依赖红线，不引入 YAML 等第三方解析库；仓库已有先例（store-jsonl 事件落盘、`MessageCodec`）。
- 拓扑的主要作者是机器（蓝图编辑器），手写次之。替代格式（YAML/TOML 等）未来可作为独立模块的加载器存在，不改变 canonical 地位。
- 编解码实现：core 的 `PipelineSpecCodec`（`read`/`write` 走 JSON 文本，`fromMap`/`toMap` 走 map 形态）。

## 2. 文档结构

一份文档表达一张图，UTF-8 编码。顶层对象字段：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `version` | 整数 | 是 | — | 格式版本，当前为 `1` |
| `name` | 字符串 | 是 | — | 图名 |
| `entry` | 字符串 | 是 | — | 入口节点 id |
| `maxIterations` | 整数 | 否 | `50` | 单次运行的节点执行上限（循环保护） |
| `nodes` | 数组 | 是 | — | 节点对象数组 |
| `edges` | 数组 | 否 | `[]` | 边对象数组 |

节点对象：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `id` | 字符串 | 是 | — | 图内唯一；边以此引用 |
| `type` | 字符串 | 是 | — | 节点类型，装配期解析为 `Node` 实现 |
| `config` | 对象 | 否 | `{}` | 节点配置（见 §3） |

边对象：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `from` | 字符串 | 是 | — | 源节点 id |
| `to` | 字符串 | 是 | — | 目标节点 id |
| `label` | 字符串 | 否 | `null` | 路由标签；缺省即默认边 |

**省略与缺省规则**：写入端产出 canonical 形态——等于缺省值的字段一律省略（`maxIterations=50` 不写、`config={}` 不写、`label=null` 不写），字段顺序固定（上表顺序）。读取端宽容：字段缺省或显式 `null` 均取缺省值，不依赖字段顺序。

## 3. 配置值域

`config` 是开放容器，延续"键名即协议"：键名由节点实现自身的契约定义（core 不约束、不枚举），自定义键建议带前缀防撞。

值必须落在 JSON 值域内：对象、数组、字符串、数字、布尔、`null`。代码装配侧若放入外来对象（如 `Duration`），导出时将报错。

数字经 JSON 文本往返后归一为 `Long`（整数）/ `Double`（浮点）——比较拓扑数据时注意代码装配的 `Integer` 与读取出的 `Long` 不相等；`toMap`/`fromMap` 的 map 形态不做归一，原样透传。

## 4. 版本策略

`version` 必填，当前为 `1`。读取遇未知版本报错（fail-fast）。格式演进靠版本号前进；不提供自动迁移——迁移工具留到有真实消费者需要时再议。

## 5. 诊断契约

两层分工，延续"诊断非门槛"哲学：

- **解析期**：格式错误（坏 JSON、字段缺失或类型错、未知版本）直接 fail-fast，异常消息带 JSON 路径定位，形如 `invalid pipeline format at $.nodes[2]: field 'id' must be a string`。文件读不出就是没有图，无诊断可言。
- **语义期**：结构合法但语义可疑（悬空引用、不可达节点等）走 `SpecValidator`，产出诊断列表（严重级 + 位置 + 消息），拦不拦由调用方决定——快照、草稿随时可存。

## 6. 表达力预留与模型说明

- **开始与结束**：格式无"开始节点/结束节点"概念。入口即 `entry` 指针；出度为零的节点即终点，运行自然结束。编辑器若需视觉锚点，由编辑器层推导或节点包提供惯例类型，与格式无关。
- **单边模型**：边只有一种——执行流向（可带路由标签）。数据不经由独立连线：运行数据是逐站传递的开放容器（不可变快照，键名即协议）。节点读写的键是节点契约的文档化约定，不是格式层的类型化端口。
- **分叉-汇合**：一个 `from` 可有多条出边、一个 `to` 可有多条入边——并行分支的拓扑在格式上本就可表达，无需为未来并行扩展改动语法。执行语义（当前为标签路由 + 默认边）由引擎定义，与文档格式解耦。
- **子图**：子图是普通节点的实现细节（节点内部执行嵌套 spec），格式不感知。其对外接口即该节点的键契约与 `config`；`config` 为开放对象，可内嵌一整份本格式文档作为子图——无需多图文档即可表达。按名引用外部图需图库，属非目标（§8）。

## 7. 示例

最小可运行形态：

```json
{
  "version": 1,
  "name": "minimal",
  "entry": "in",
  "nodes": [
    { "id": "in", "type": "input" },
    { "id": "out", "type": "output" }
  ],
  "edges": [
    { "from": "in", "to": "out" }
  ]
}
```

带路由标签的边（网关节点的分支）：

```json
{ "from": "has-tools", "to": "exec", "label": "true" }
```

完整的真实示例见 nodes-agent 内置默认聊天链 [`nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json`](../nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json)——默认链本身就以此格式发布，运行期经 `PipelineSpecCodec` 加载。

## 8. 非目标（本期明确不做）

- 多图文档 / 图库：留到 server 实例有真实需要时。
- 自动迁移工具：见 §4。
- YAML/TOML 加载器进 core：替代格式归独立模块。
- 美化（pretty）输出：写入端当前产出紧凑单行 JSON；美化输出留待蓝图编辑器落盘需求定夺。
