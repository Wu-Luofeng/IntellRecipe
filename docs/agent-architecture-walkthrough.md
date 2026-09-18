# Agent 架构逐文件精讲（首次自建 Agent 教学版）

> 对象：`agent-service/` 的全部代码。读完你应该能白板手画出整个框架。
> 讲解顺序 = 一次请求流经的顺序，而不是文件名字母序。

---

## 0. 全景：一次对话到底发生了什么

```
用户: "今晚吃点什么好？"
  │
  ▼
main.py  POST /agent/chat (或 /agent/chat/stream)
  │  组装 ToolContext{user_id, token}          ← 可信身份，LLM 碰不到
  ▼
loop.py  AgentLoop.run()                      ← ① 用户消息进历史
  │
  │  ┌─────────────── ReAct 循环（最多 6 轮）───────────────┐
  ▼  ▼                                                      │
provider.py chat(messages+工具说明书)  → LLM                │
  │                                                       │
  ├─ LLM 返回 tool_calls? ──是──▶ registry.execute()       │
  │                                │ 工具查接口/算数        │
  │                                ▼ 结果 append 回历史 ────┘ 再问 LLM
  │
  └─ 否（纯文本）──▶ 这就是最终回答，写入历史，返回
```

**一句话心智模型**：Agent = 一个while循环，循环体是「把消息列表发给 LLM → LLM 要么回答要么点名调工具 → 执行工具把结果塞回消息列表 → 再发给 LLM」。其他所有文件都是为这个循环服务的：`provider` 管"怎么调 LLM"，`registry+tools` 管"有哪些工具"，`session` 管"消息列表存哪"，`context` 管"第一条消息写什么"。

---

## 1. `app/config.py` —— 配置层（最简单，先热身）

```python
@dataclass
class LLMConfig:
    base_url: str = "https://api.deepseek.com/v1"
    model: str = "deepseek-chat"
    api_key: str = ""
    ...
```

**要点**：
- dataclass 树形结构，`load_config()` 读 `config.yaml` 后按 key 组装；
- `_load_dotenv()` 手写了一个 20 行的 .env 解析器而没引入 python-dotenv——**依赖越少越不容易坏**；
- 密钥的加载优先级：环境变量 > .env > yaml——**密钥永远不进 git**（`.gitignore` 里的 `**/.env`）。

新手启示：配置层不值得炫技，能"一处改、全局生效"即可。

---

## 2. `app/core/provider.py` —— LLM 适配层（换厂商只改这一个文件）

### 2.1 为什么要有 Provider 抽象

DeepSeek / 通义 / vLLM 的 HTTP 协议都是 OpenAI 兼容的，但 URL、鉴权、错误码各有差异。框架里**只有这个文件知道"LLM 是通过 HTTP 调的"**，其余代码只面对两个方法：

```python
async def chat(self, messages, tools=None) -> dict        # 一次性返回完整 assistant 消息
def   chat_stream(self, messages, tools=None) -> AsyncIterator[dict]   # 流式，逐 chunk yield
```

这就是 Nanobot 的核心经验：**所有厂商差异收敛到一个接口**。

### 2.2 chat() 的返回值为什么直接是 message dict

```python
return resp.json()["choices"][0]["message"]
```

返回的 `{"role": "assistant", "content": ..., "tool_calls": [...]}` **本身就是 OpenAI 协议的历史消息格式**，调用方可以原样 append 进对话历史，不需要二次转换。少一次映射 = 少一类 bug。

### 2.3 重试策略的分寸

```python
RETRYABLE_STATUS = {429, 500, 502, 503, 504}   # 只有这些才重试
```

- 429（限流）/ 5xx（服务端错）→ 指数退避重试（0.5s → 1s → 2s）；
- 400（参数错）→ 重试也没用，直接抛；
- **流式版本的重试只覆盖"首块到达前"**（`started` 标志）：已经开始往用户那里吐字后再失败，重试会导致输出重复——所以直接抛错交给上层。

面试点：**幂等边界决定重试边界**。请求还没产生副作用（没输出任何字节）才允许重试。

### 2.4 chat_stream 的本质：HTTP 连接保持打开，服务器分多次推 JSON

普通请求：`POST` → 服务器**算完整个回答** → 一个大 JSON 返回，客户端干等。

流式请求：请求体加 `"stream": true`，服务器立刻返回响应头（`Content-Type: text/event-stream`），**HTTP 连接不关闭**，每算出一个 token 就推一行。线上真实抓包长这样：

```
HTTP/1.1 200 OK
Content-Type: text/event-stream

data: {"choices":[{"delta":{"content":""}}]}          ← 第1块（角色声明）
data: {"choices":[{"delta":{"content":"今"}}]}        ← 第2块（一个 token）
data: {"choices":[{"delta":{"content":"天"}}]}        ← 一个 token 可能是多个字
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"he"}}]}}]}
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ight_cm\": 175}"}}]}}]}
data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}   ← 本轮结束信号
data: [DONE]                                          ← 整个流的终止标记
```

客户端逐行解析的三个动作（对应 `chat_stream` 的三行代码）：

| 代码 | 在做什么 |
|---|---|
| `resp.aiter_lines()` | **按行挂起等待**：socket 来了一行就恢复执行 yield，没来就 suspend。这就是"流式感"的全部来源——没有定时器，是异步 IO 对 socket 的原生封装 |
| `line[5:].strip()` | 剥掉 SSE 协议的 `data: ` 前缀 |
| `yield json.loads(data)` | **立刻**把原始 chunk 交给上层——不等整个流结束 |

**Provider 不做任何累积**：因为"delta 怎么拼"取决于 chunk 里是什么——content 直接拼，tool_calls 的 `arguments` 会被拆成碎片必须按 index 拼接（上例 `{"he` + `ight_cm": 175}` = `{"height_cm": 175}`）。累积是 Agent 循环（run_stream）的职责，见 8.2。

---

## 3. `app/tools/base.py` —— 工具抽象（function calling 的协议本质）

```python
class Tool(ABC):
    name: ClassVar[str] = ""
    description: ClassVar[str] = ""
    parameters: ClassVar[dict] = {"type": "object", "properties": {}}

    @abstractmethod
    async def execute(self, ctx: ToolContext, **kwargs) -> dict: ...

    def to_schema(self) -> dict:
        return {"type": "function",
                "function": {"name": ..., "description": ..., "parameters": ...}}
```

**这是理解 Agent 的关键一课**。所谓"给 LLM 工具"，发到 API 里的其实是三段说明书：

| 字段 | 谁读 | 作用 |
|---|---|---|
| `name` | LLM | 调用时引用的函数名 |
| `description` | LLM | **路由依据**——LLM 靠这段话决定"什么问题该用这个工具" |
| `parameters`(JSON Schema) | LLM | 参数该怎么填、类型是什么 |

LLM 收到用户问题后，**输出的不是代码，而是一段 JSON**："请帮我调用 `calc_health_metrics`，参数是 `{height_cm: 175, ...}`"。真正执行函数的是你的 Python 代码。这就是 function calling 的全部秘密。

所以 `description` 不是文档，是**提示词的一部分**——写得含糊，LLM 就会选错工具或乱填参数。

---

## 4. `app/tools/base_ctx.py` —— 工具上下文（安全边界）

```python
@dataclass
class ToolContext:
    user_id: int | None = None
    authorization: str | None = None
```

只有 8 行，但它是整个服务的**安全设计核心**：

- `user_id` 和 `token` 来自 HTTP 请求，**注入给工具执行，但不放进 LLM 的可见信息里**；
- LLM 的参数 schema 里没有 user_id——它想查别人的数据也编不出入口；
- 对比反面设计：把 user_id 放进 parameters 让 LLM 填 → LLM 被提示词注入后可以横向越权查任何人的健康数据。

教训：**可信数据走 context，不可信数据走 LLM 参数**。

---

## 5. `app/core/registry.py` —— 注册中心（工具的"路由器 + 防火墙"）

三个职责：

### 5.1 schema 导出（稳定排序）

```python
def schemas(self) -> list[dict]:
    return [self._tools[name].to_schema() for name in sorted(self._tools)]
```

按名字排序看似多余，实际是为了 **prompt cache**：每次请求都带全量工具说明书，顺序稳定才命中 LLM 提供方的缓存，省钱且降延迟。

### 5.2 执行包装（错误隔离 + 截断）

```python
async def execute(self, name, args, ctx) -> str:
    tool = self._tools.get(name)
    if tool is None:
        return json.dumps({"ok": False, "error": f"未知工具: {name}"})
    try:
        data = await tool.execute(ctx, **args)
    except Exception as e:
        data = {"ok": False, "error": f"{type(e).__name__}: {e}"}
    text = json.dumps(data, ensure_ascii=False, default=str)
    if len(text) > MAX_TOOL_RESULT_CHARS:
        text = text[:MAX] + "...(截断)"
    return text
```

两个设计决策值得背下来：

1. **工具异常不抛出，而是包装成结果喂回 LLM**。比如"接口超时"作为一种工具结果，LLM 会自己调整话术说"服务暂时不可用"——这就是之前实测看到的优雅降级。若直接抛异常，整个循环崩掉，用户体验是 500。
2. **结果截断**。一次查询返回 50KB JSON 会把上下文窗口撑爆（也烧钱）。截断后 LLM 拿到头部的关键数据，依然能回答。

### 5.3 parse_tool_args

```python
def parse_tool_args(raw: str) -> dict:
    return json.loads(raw)
```

**新手第一大坑**：LLM 返回的 `tool_calls[i].function.arguments` 是**字符串**（JSON 序列化后的），不是对象。忘了 parse 直接当 dict 用会炸。单独拆个函数 + 异常处理，是因为 LLM 偶尔会生成不合法 JSON。

---

## 6. `app/core/session.py` —— 会话记忆（历史即消息数组）

```python
class SessionStore:
    def history(self, session_id) -> list[dict]: ...
    def append(self, session_id, message): ...
    def _trim(self, session_id): ...
```

- **没有魔法**：每个 session 就是一个 OpenAI messages 数组。多轮对话 = 数组越来越长。
- `_trim()` 有个容易被忽略的细节：裁剪后如果数组**第一条是 tool 消息**，必须继续丢——因为 OpenAI 协议要求 tool 消息必须紧跟在带 `tool_calls` 的 assistant 消息后面，孤儿的 tool 消息会让 API 直接报 400（Nanobot 里这叫"declared vs fulfilled 校验"，是它踩过坑后加的）。
- `dump()` 只回放 user/assistant 的文本——给页面刷新后重渲染用，工具调用的中间痕迹不用给用户看。
- 内存 dict 是刻意选的：**接口不变，换 Redis 就是替换这一个类**。

---

## 7. `app/core/context.py` —— 系统提示词（Agent 的"宪法"）

```
你是「智膳助手」…
## 工具使用规则
- 推荐菜品必须先调用 recommend_dishes…绝不编造菜品
- 算术调用 calc_health_metrics，不要心算
## 安全与边界
- 疾病诊疗必须建议咨询医生…
```

三段式结构值得抄走：**人设（你是谁）→ 规则（何时用哪个工具）→ 边界（什么不能做）**。

两个实战细节：
- `{user_id}` 占位符注入当前用户——告诉 LLM"身份已自动携带，别问、别传参"，从提示词层面配合 ToolContext 的安全设计；
- `HEALTH_DISCLAIMER` 是代码里 append 的而不是让 LLM 记得写——**合规兜底不能依赖模型自觉**。

---

## 8. `app/core/loop.py` —— ReAct 主循环（整个框架的心脏）

### 8.1 同步版 run() 逐段精读

```python
history.append({"role": "user", "content": message})        # ①

for _ in range(self._max_iterations):                        # ② 防死循环，硬上限 6 轮
    assistant = await self._provider.chat([system] + history, tools)   # ③ 系统提示词 + 历史 + 工具说明书

    tool_calls = assistant.get("tool_calls") or []
    if not tool_calls:                                       # ④ 终止条件：没有工具调用 = 最终回答
        content = (assistant.get("content") or FALLBACK_REPLY).strip()
        history.append({"role": "assistant", "content": content})
        return {"reply": content, "tool_trace": trace}

    history.append({"role": "assistant", ..., "tool_calls": tool_calls})  # ⑤ 先记账再执行

    for call in tool_calls:                                  # ⑥ 可能一次调多个工具
        args = parse_tool_args(call["function"]["arguments"])
        result = await self._registry.execute(name, args, ctx)
        if name in HEALTH_TOOLS: health_related = True
        history.append({"role": "tool", "tool_call_id": call["id"], "content": result})  # ⑦ 结果回填

    # 回到循环顶部：带着工具结果再问一次 LLM
```

必须吃透的四个点：

1. **⑤ 先 append 再执行**：assistant 的 tool_calls 意图必须先进历史，然后每个 tool 结果用 `tool_call_id` 配对回填。**顺序和配对错一个，下次请求就 400**。
2. **④ 终止条件是"LLM 不再要工具"**，而不是"任务完成"——框架无法判断任务完成，只有 LLM 自己知道。
3. **② 迭代上限**是安全阀：LLM 陷入"调工具→不满→再调"的死循环时，6 轮后强制给出兜底话术。
4. **③ 每轮都重发系统提示词 + 全量历史**：LLM API 是无状态的，"上下文"完全是客户端把历史数组重发出来的假象。理解这一点，session/裁剪/缓存就全都通了。

### 8.2 流式版 run_stream() 的三个增量难点

```python
async for chunk in self._provider.chat_stream(...):
    delta = choice.get("delta") or {}
    if piece := delta.get("content"):
        content_acc += piece
        yield {"type": "delta", "content": piece}          # 增量立刻推给前端
    for tc in delta.get("tool_calls") or []:
        acc = tc_acc.setdefault(tc.get("index", 0), {"id":"", "name":"", "args":""})
        acc["name"] += fn.get("name") or ""                # 分片累积！
        acc["args"] += fn.get("arguments") or ""
```

1. **流式下 tool_calls 是碎片**：`arguments` 一个 JSON 会被拆成 N 个 chunk 发来，必须按 `index` 累积，结束才能 parse——不能用非流式的处理方式。
2. **`yield {"type": "tool"}`**：工具执行完成立刻通知前端显示"🔧 xx 完成"，用户不用盯着空白等。
3. **`round` 事件**：多轮之间 yield 一个轮次标记，前端重置文本缓冲——否则第 1 轮的"思考旁白"和第 3 轮的最终答案会连在一个气泡里。
4. **记账一致性**：`done` 事件的 `reply` 与 history 里 append 的 assistant content 必须同源（含免责声明），否则页面显示和下一轮的上下文会分叉。

### 8.3 轮次 vs 工具数：两个不同层级的计数器（高频疑问）

外层 `for`（轮次）和内层 `for call in tool_calls`（工具）是**两个独立计数**：

```
外层一轮 = 调用一次 LLM
内层 N 次 = LLM 在同一轮里点名了多少个工具（并行工具调用，都算同一轮）
```

以实测记录为例：

```
round#0: LLM → [get_user_context]              ← 1 个工具 = 1 轮
round#1: LLM → [calc_health_metrics]           ← 1 个工具 = 1 轮
round#2: LLM → [recommend_dishes]
round#3: LLM → 无 tool_calls，纯文本 → 终止     ← 共 4 轮、4 次工具
```

如果某轮 LLM 同时点名 `[search_dishes, get_user_context]` 两个工具，内层循环各执行一次，**仍只消耗 1 个轮次**。所以：

- **"轮次会不会变"只取决于 LLM 下一轮还要不要工具**，与单轮工具个数无关；
- 若 LLM 原地打转（每轮重复点名同样的工具），轮次持续增长 → **被 `max_iterations=6` 硬上限截停**，给出兜底话术；
- 防原地打转的第四道防御（本次新增，`run`/`run_stream` 均有）：**本次消息内的 memo**——`工具名+参数` 相同的调用直接返回 `{"cached": true, "note": "已用相同参数调用过…", "previous_result": ...}`，不再真执行。已有 `scripts/test_loop_memo.py` 用 FakeProvider 离线验证（2 个并行工具=1 轮、重复调用命中缓存、3 次 LLM 调用收尾）。

为什么可以这样防：工具结果是**幂等读**（重复调返回相同数据），把"重复了"这个事实作为 tool 结果喂回 LLM，它就有足够信息停止循环；若是写操作（如下单），幂等性由业务侧 CAS 保证（参见订单服务的 clientToken 设计）。

---

## 9. `app/core/api_client.py` —— 数据通道（"Python 不碰库"的执行者）

```python
async def request(self, ctx, method, path, query=None, json_body=None) -> dict:
    if not path.startswith("/") or ".." in path: return {"ok": False, ...}
    url = f"{self._base}{path}"
    if not url.startswith(self._base): return {"ok": False, ...}     # 双保险
    headers["authorization"] = ctx.authorization                     # 凭证透传
    ...
    return {"ok": ..., **self._unwrap(body)}                         # 解 Java Result 信封
```

- **白名单双重校验**：入口查路径格式 + 出口再查最终 URL 前缀。LLM 任何形式的注入（`//evil.com`、`..`）都到不了外网；
- `_unwrap()` 把 Java 的 `{success, data, errorMsg}` 统一拆成 `{"ok", "data"/"error"}`——Java 侧返回结构变化时只改这里；
- `field(row, "cuisineType", "cuisine_type")` 兼容 camelCase/snake_case，Java 序列化风格变了也不用全改工具。

---

## 10. 具体工具：从简到繁的三个范本

### 10.1 `health_calc.py` —— 最简范本（纯函数工具）

```python
class CalcHealthMetricsTool(Tool):
    name = "calc_health_metrics"
    parameters = {..., "required": ["height_cm", "weight_kg"]}

    async def execute(self, ctx, **kwargs):
        bmi = w / ((h/100) ** 2)
```

要点：**算术必须放工具里**。LLM 心算 BMI 经常错一位小数，而"算错健康数值"是业务事故。参数校验（80≤h≤250）挡住 LLM 幻觉出的荒谬输入。

### 10.2 `http_api.py` —— 动态调用范本

LLM 自己决定 path 和 query，工具只管安全边界。`description` 里内嵌了一份"已知接口清单"，相当于把路由表喂给 LLM。

### 10.3 `dish_recommend.py` —— 编排范本（最复杂，也最能体现 Agent 价值）

```python
profile = await client.get_json(ctx, ep["user_profile"])    # 调接口 1：身体数据
pref    = await client.get_json(ctx, ep["user_preference"]) # 调接口 2：偏好
rows    = await client.get_json(ctx, ep["recipes_search"])  # 调接口 3：菜品粗筛
# —— 以下是 Python 侧的分析逻辑（LLM 干不了这些确定性计算）——
for row in rows:
    if any(tok in text for tok in avoid_tokens): continue   # 忌口硬过滤
    score = 菜系匹配*3 + 口味匹配*2 + 热量贴合度*2 + 高蛋白*1
dishes = sorted(scored, key=score)[:limit]                   # 交给 LLM 组织成人话
```

**一个工具内完成三次 API 编排 + 打分排序**，返回给 LLM 的是已经算好的结构化结果。这就是"LLM 负责 NLU 和表达，确定性逻辑负责计算"的分工范式。

---

## 11. `app/tools/factory.py` —— 装配（组合根）

```python
def build_registry(cfg: APIConfig) -> ToolRegistry:
    client = ApiClient(cfg.gateway_base, ...)
    registry = ToolRegistry()
    registry.register(RecommendDishesTool(client, cfg.endpoints))
    ...
```

依赖注入的最简形态：所有工具在这里创建、接线。想加工具 = 写类 + 加一行。**曾经踩过的坑**：把 `build_registry` 放在 `tools/__init__.py` 造成循环导入（`__init__` → core → `tools/base` → `__init__`）——所以装配代码永远放独立模块。

---

## 12. `app/main.py` —— 服务外壳

```python
@asynccontextmanager
async def lifespan(app):
    cfg = load_config()
    registry = build_registry(cfg.api)          # 组合根
    provider = OpenAICompatProvider(...)
    app.state.loop = AgentLoop(provider, registry, sessions, ...)
    yield
    await provider.close()                      # 优雅关闭
```

- **lifespan 管资源生命周期**：启动时装配，关闭时释放 HTTP 连接池；
- **app.state 做穷人版 DI**：路由处理函数从 `app.state.loop` 拿依赖，不搞全局 import；
- 踩过的坑：`db.py` 最初在 lifespan 里**强制连库**，MySQL 没起整个服务都起不来 → 改成懒加载（用到才连）。**基础设施不可用应该降级，不该阻断启动**。

---

## 13. 设计决策速查表（面试快答）

| 决策 | 理由 |
|---|---|
| 自研循环而不是 LangChain | 核心循环只有 100 行，引入框架换来黑盒调试成本，不值 |
| ToolContext 与 LLM 参数隔离 | 防提示词注入导致横向越权 |
| 工具异常包装成结果 | LLM 自我纠正，循环不崩 |
| 结果截断 | 保上下文窗口 + 控成本 |
| schema 稳定排序 | 命中 LLM 供应商的 prompt cache |
| run/run_stream 双入口 | 会话记账同源，流式只是"表达方式"不同 |
| Python 不碰库 | 数据职责留 Java，Agent 层无状态、易横向扩展 |

---

## 14. 新手七大坑（每个都对应本框架的一处防御）

1. tool 结果忘 append 回历史 → 下轮请求 400（框架在 ⑦ 步集中处理）
2. `arguments` 当对象用（它是字符串）→ `parse_tool_args`
3. tool 消息缺 `tool_call_id` 或顺序错 → append 顺序固定 + dump 时防孤儿
4. 无迭代上限 → 死循环烧钱 → `max_iterations=6`
5. LLM 心算 → 数值类工具（health_calc）
6. 工具异常打崩循环 → Registry 包装
7. user_id 让 LLM 填 → ToolContext 注入

---

## 15. 建议的练手扩展（按难度排序）

1. **加一个"记录用户偏好"工具**（写操作，练习：POST 透传 + 写后读校验）；
2. **会话持久化到 Redis**（练习：替换 SessionStore 实现，TTL 过期）；
3. **工具结果缓存**（同参数 5 分钟内不重复调接口，练习：装饰器）;
4. **子 Agent**（让 recommend 工具内部再起一个小循环，练习：Nanobot 的 subagent 思想）；
5. **评估集**（准备 20 个问题+期望调用的工具序列，每次改提示词后回归，练习：Agent 的"测试"长什么样）。
