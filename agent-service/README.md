# IntellRecipe Agent Service（智膳助手）

IntellRecipe 的 **AI Agent 独立服务**：Python + FastAPI + 自研最小 Agent 框架（借鉴 Nanobot 设计思想，未引入 LangChain）。

**架构原则：Python 不直连数据库。** 所有业务数据通过 Java 网关接口获取，Python 只负责「调用接口 + 分析结果 + 组织回答」。数据访问职责全部留在 Java 侧。

## 能力

| 能力 | 实现方式 |
|---|---|
| 健康问答 | LLM 直答 + `calc_health_metrics` 算 BMI/BMR/TDEE（算术不靠 LLM 心算）+ `get_user_context` 聚合用户档案/偏好/今日摄入 |
| 菜品推荐 | `recommend_dishes`：调 Java 接口取用户偏好与菜品列表 → Python 打分（忌口硬过滤、菜系/口味加分、热量贴近单餐预算）|
| 菜品搜索 | `search_dishes`：关键词/菜系/口味/热量上限/蛋白下限条件查询 |
| 动态调接口 | `call_api`：LLM 决定调哪个接口，服务侧统一白名单 + 凭证透传 |

## 架构

```
浏览器 ──GET /chat──▶ FastAPI（本服务 :8800）
                         │
                    AgentLoop（ReAct, max_iterations=6）
                         │
        ┌────────────────┼───────────────────┐
        ▼                ▼                   ▼
   LLMProvider      ToolRegistry        SessionStore
 （DeepSeek 兼容）  （schema+执行+截断） （多轮上下文）
                         │
   ┌──────────┬──────────┼────────────┬──────────┐
   ▼          ▼          ▼            ▼          ▼
recommend  search     get_user    calc_health   call_api
_dishes    _dishes    _context    _metrics
   └─────────┴─────┬────┘                        │
                   ▼                             ▼
          ApiClient（统一封装）──────────▶ IntellRecipe 网关 :10010
          （域名白名单/Result信封解包/            │
            token透传/超时截断）                  ▼
                                       Java 服务（查库等数据职责）
```

## Java 接口契约（Python 侧依赖的数据接口）

已存在的接口（直接可用）：
- `GET /user/me` —— 当前用户信息（需 `authorization` 头）
- `GET /diet/today` —— 当前用户今日饮食摄入

计划新增的接口（路径在 `config.yaml → api.endpoints` 可调，Java 侧实现后 Agent 立即生效）：

| 接口 | 方法 | 参数 | 返回（Result 信封） |
|---|---|---|---|
| `/recipe/agent/preference` | GET | 无（token 识别用户） | `{cuisinePreference, tastePreference, avoidIngredients, healthGoal, dailyCalorieTarget}` |
| `/recipe/agent/recipes` | GET | `keyword?/cuisine?/taste?/maxCalories?/minProtein?/limit?` | 菜品数组：`[{id, name, description, cuisineType, tasteProfile, calories, protein, fat, carbs}]` |

说明：字段名兼容 camelCase / snake_case 两种风格（`field()` 助手自动适配）；未实现的接口会被工具优雅报错，LLM 会告知用户"服务暂不可用"而不是崩溃。

## 运行

```bash
cd agent-service
pip install -r requirements.txt
copy .env.example .env      # 填入 LLM_API_KEY
python run.py               # http://localhost:8800
```

## 接口

```bash
# 对话（user_id 由前端传入；登录 token 经 authorization 头透传给 Java 接口）
curl -X POST http://localhost:8800/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"今晚吃点什么好？","user_id":1,"session_id":"abc",
       "authorization":"<前端登录token>"}'

curl http://localhost:8800/agent/tools    # 工具 schema
```

独立对话页面：浏览器打开 **`http://localhost:8800/chat`**——支持 Markdown 渲染、工具调用轨迹折叠展示、会话上下文跨刷新保持（session_id 存 localStorage + 服务端 SessionStore）、一键新对话。

## 新增一个工具（三步）

1. `app/tools/` 新建类继承 `Tool`，声明 `name/description/parameters`，实现 `async execute(ctx, **kwargs)`；
2. `app/tools/factory.py` 注册一行；
3. 完成——LLM 通过 function calling 自动发现并调用。

## 安全设计（面试要点）

- **用户身份不进 LLM**：`user_id`/token 注入 `ToolContext`，工具从 context 取——LLM 编参数查不了别人的数据；
- **call_api 集中管控**（在 ApiClient）：域名白名单、仅 GET/POST、路径校验、二次前缀校验、LLM 永远接触不到凭证；
- **防幻觉**：推荐只能基于接口返回的真实菜品，工具结果超长自动截断；
- **医疗边界**：疾病类问题强提示词引导"咨询医生"并附免责声明；
- **优雅降级**：任一上游接口不可用时工具返回结构化错误，LLM 调整回答而非崩溃。

## Roadmap

- [ ] Java 侧实现 `/recipe/agent/preference` 与 `/recipe/agent/recipes` 两个契约接口
- [ ] 流式输出（SSE）
- [ ] 会话持久化到 Redis（SessionStore 接口不变，替换实现）
- [ ] 偏好写入工具（agent 反向沉淀用户偏好）
