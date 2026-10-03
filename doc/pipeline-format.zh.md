# 图拓扑格式（Pipeline Format）

> Brick 的图拓扑是数据。本文档定义拓扑的 canonical 表达——JSON 格式。蓝图编辑器、server 实例、手写配置共用这一种格式。
>
> English version: [pipeline-format.md](pipeline-format.md)；两版内容分歧时以中文版为准。

## 1. 地位与选型

- **canonical 格式 = JSON**。理由：与 spec 记录模型（`PipelineSpec`/`NodeSpec`/`EdgeSpec`/`PinRef`）同构，映射近乎 1:1；core 守零依赖红线，不引入 YAML 等第三方解析库；仓库已有先例（store-jsonl 事件落盘、`MessageCodec`）。
- 拓扑的主要作者是机器（蓝图编辑器），手写次之。替代格式（YAML/TOML 等）未来可作为独立模块的加载器存在，不改变 canonical 地位。
- 编解码实现：core 的 `PipelineSpecCodec`（`read`/`write` 走 JSON 文本，`fromMap`/`toMap` 走 map 形态）。

## 2. 文档结构

一份文档表达一张图，UTF-8 编码。顶层对象字段：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `version` | 整数 | 是 | — | 格式版本，当前为 `2` |
| `name` | 字符串 | 是 | — | 图名 |
| `maxFirings` | 整数 | 否 | `50` | 单次运行的节点触发总次数上限（循环保护） |
| `inputs` | 数组 | 否 | `[]` | 引脚对象数组；run 的外部注入点 |
| `outputs` | 数组 | 否 | `[]` | 引脚对象数组；run 结果的取值点 |
| `nodes` | 数组 | 是 | — | 节点对象数组 |
| `edges` | 数组 | 否 | `[]` | 边对象数组 |

节点对象：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `id` | 字符串 | 是 | — | 图内唯一；边与 inputs/outputs 以此引用 |
| `type` | 字符串 | 是 | — | 节点类型，装配期解析为 `Node` 实现 |
| `config` | 对象 | 否 | `{}` | 节点配置（见 §3） |

引脚对象（`inputs`/`outputs` 的元素）：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `node` | 字符串 | 是 | — | 节点 id |
| `key` | 字符串 | 是 | — | 引脚名（节点本地命名） |

边对象：

| 字段 | 类型 | 必填 | 缺省值 | 说明 |
|---|---|---|---|---|
| `from` | 引脚对象 | 是 | — | 源引脚（某节点的输出引脚） |
| `to` | 引脚对象 | 是 | — | 目标引脚（某节点的输入引脚） |

**省略与缺省规则**：写入端产出 canonical 形态——等于缺省值的字段一律省略（`maxFirings=50` 不写、空 `inputs`/`outputs` 不写、`config={}` 不写），字段顺序固定（上表顺序）。读取端宽容：字段缺省或显式 `null` 均取缺省值，不依赖字段顺序。

## 3. 配置值域

`config` 是开放容器，延续"键名即协议"：键名由节点实现自身的契约定义（core 不约束、不枚举），自定义键建议带前缀防撞。

值必须落在 JSON 值域内：对象、数组、字符串、数字、布尔、`null`。代码装配侧若放入外来对象（如 `Duration`），导出时将报错。

数字经 JSON 文本往返后归一为 `Long`（整数）/ `Double`（浮点）——比较拓扑数据时注意代码装配的 `Integer` 与读取出的 `Long` 不相等；`toMap`/`fromMap` 的 map 形态不做归一，原样透传。

## 4. 版本策略

`version` 必填，当前为 `2`。读取遇非 `2` 版本报错（fail-fast，`unsupported format version`）。

**变更记录**：
- `v1`：0.2.0 开发期草稿（执行线模型：entry 指针、带路由标签的边、`maxIterations`），从未随 release 发布，v2 落地时就地退役，无自动迁移。
- `v2`：数据流模型。边 = 引脚到引脚的绑定对象（`from`/`to` 为引脚对象）；`entry` 消亡，`inputs`/`outputs` 声明 run 的注入点与取值点；`maxIterations` 转世为 `maxFirings`。
- `v3`（当前，模型层）：循环容器化——`loop` 节点（config 内嵌 body 文档、while 基语义、携带值同名规则、输出声明可选 `as` 暴露名）替代图内回边循环；`branch` 统一替代 if/switch；`NodeContract` 收敛为两分量 `reads`/`writes`（context、dynamic 分量均删除——引脚一律显式，可由 `contract(config)` 从 config 枚举）；外层图与循环体皆为 DAG。磁盘格式未变（`version` 仍为 `2`；`as` 由容器节点读取，codec 宽容忽略未知字段，存量文档不受影响）。

格式演进靠版本号前进；不提供自动迁移——迁移工具留到有真实消费者需要时再议。

## 5. 诊断契约

两层分工，延续"诊断非门槛"哲学：

- **解析期**：格式错误（坏 JSON、字段缺失或类型错、未知版本）直接 fail-fast，异常消息带 JSON 路径定位，形如 `invalid pipeline format at $.nodes[2]: field 'id' must be a string`。文件读不出就是没有图，无诊断可言。
- **语义期**：结构合法但语义可疑走 `SpecValidator`。结构错误（重复节点 id、边/inputs/outputs 引用未知节点）为 error，是运行前的唯一门槛；引脚级发现（未知引脚、类型族不匹配、一引脚多入线、未连线 reads、无消费者 writes）一律 warning——快照、草稿随时可存，图随时可运行。

## 6. 模型说明（数据流模型）

- **边 = 绑定**：边携带一个类型化的命名值，从源节点的输出引脚注入目标节点的输入引脚（`m = A.getX()`；`B.setU(m)`）。执行顺序由数据依赖推导，不存在独立的执行线。
- **引脚 = 节点的命名端口**：`NodeContract` 的 reads/writes 是真实端口定义；引脚名节点本地，连线负责翻译（A 的 `x` 可连 B 的 `u`）。
- **触发规则（粘性绑定）**：值一旦注入某引脚就一直在（后写覆盖先写）；节点的 reads 全部有绑定即触发一次；触发后任一 read 来了新值即再触发。零 read 的节点（纯源）开局即触发。外层图与每个循环体内部都是 DAG——每节点每轮至多触发一次，同一最简规则处处成立，没有冷热引脚之分。
- **选择性投递（条件 = 选中的键）**：`branch` 节点（控制结构族）的输出 map 只含被选中分支的键；引擎按"键在不在"沿出边投递，未选中分支的引脚永远收不到值、其后继永不触发。`control` 为 BOOLEAN 时免 config（两引脚 `true`/`false`），否则按 config `cases` 匹配、都不中走 `default`。
- **多入线投递（汇合）**：一个输入引脚允许多根入线：每次到达都投递、后写覆盖。体内分支的汇合用平凡 `pass` 节点（原样转发）并成一根确定的输出线。
- **同值不重复投递**：重投递的值与引脚上现值相等（`equals`）时不视为新值、不触发下游——幂等写不会打出循环。
- **循环 = loop 容器节点**：重复不进图。`loop` 节点（nodes-basic）的 config 内嵌一份完整 body 文档（本格式，含 inputs/outputs 声明）：`{"body": {…}, "condition": {"pin": <body 输出 key>, "initial": <值>}, "maxIterations": 50}`。基语义 = while（先判后跑）：读条件携带值（首轮取 `initial`，之后取上一轮 body 的同名输出），假 → 携带值终值写到容器输出引脚；真 → 以当前携带值为初始绑定完整运行 body 一遍（内层引擎递归，事件经外层观察线转发），body 声明的 outputs 更新携带值，回到条件判断。**携带值 = body 的 inputs ∩ outputs（同名）**：inputs 决定容器的输入引脚（首轮注入、逐轮重注），outputs 决定容器的输出引脚（终值产出）；同名者天然跨迭代携带；条件引脚本身也是携带值（每轮由 body 重算）。输出声明可带可选 `as` 字段以另一名字暴露（body 生产引脚与携带名不同名时，如 `{"node": "pass", "key": "value", "as": "messages"}`）。body 不再产出的输入（如配置）天然跨轮保持其注入值。**零迭代输出规则**：body 从未运行时，容器输出取携带值的初始值；既无初始又无注入的携带输出缺失（校验 warning，不拦保存）。`maxIterations` 撞顶抛错（循环保护）。`condition.pin` 不在 body outputs 里时 body 无从翻转条件：initial 为真则必然转满 `maxIterations`、为假则必然零迭代——执行时发一条 `warning` 事件（诊断非门槛），运行继续。
- **开始与结束**：格式无"开始节点/结束节点"。起点 = 输入齐备的节点（含 run 注入）；没有新投递可触发任何节点即静默终止，`run-end` 事件报告触发总次数与从未触发的节点。
- **分叉-汇合**：无依赖的分支天然可先后触发（并行执行留待引擎支持，语义已兼容）；多入线即汇合。
- **子图**：子图是普通节点的实现细节（节点内部执行嵌套 spec），格式不感知。其对外端点 = 它在父图上的引脚；`config` 为开放对象，可内嵌一整份本格式文档作为子图。按名引用外部图需图库，属非目标（§8）。

## 7. 示例

最小可运行形态（run 注入 `in.text`，从 `out.output` 取值）：

```json
{
  "version": 2,
  "name": "minimal",
  "inputs": [{ "node": "in", "key": "text" }],
  "outputs": [{ "node": "out", "key": "output" }],
  "nodes": [
    { "id": "in", "type": "input" },
    { "id": "out", "type": "output" }
  ],
  "edges": [
    { "from": { "node": "in", "key": "messages" },
      "to":   { "node": "out", "key": "messages" } }
  ]
}
```

完整的真实示例见 nodes-agent 内置默认聊天链 [`nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json`](../nodes-agent/src/main/resources/tech/forethought/brick/nodes/agent/chat.json)——默认链本身就以此格式发布，运行期经 `PipelineSpecCodec` 加载。

## 8. 非目标（本期明确不做）

- 多图文档 / 图库：留到 server 实例有真实需要时。
- 自动迁移工具：见 §4。
- YAML/TOML 加载器进 core：替代格式归独立模块。
- 美化（pretty）输出：写入端当前产出紧凑单行 JSON；美化输出留待蓝图编辑器落盘需求定夺。
