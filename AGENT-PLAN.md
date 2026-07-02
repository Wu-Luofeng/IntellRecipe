# 智能营养顾问 Agent 规划文档 v5.0

> 对话式 AI Agent，复用 diet-service 的饮食记录能力，专注营养规划和食谱推荐

---

## 一、职责划分

### 1.1 diet-service（已有）

**职责**：饮食记录与热量计算

**接口**：
- `GET /diet/today` → 今日饮食（总热量 + 各条目详情）
- `POST /diet/entry` → 添加饮食记录
- `DELETE /diet/entry/{id}` → 删除饮食记录

**数据**：
- `diet_log` 表：记录每次饮食的食材、克数、热量、餐次

### 1.2 recipe-service（新建）

**职责**：营养规划 + 食谱推荐 + 对话交互

**核心功能**：
1. 根据用户信息计算营养目标（BMR/TDEE/每餐分配）
2. 调用 diet-service 获取今日摄入
3. 对比摄入 vs 目标，推荐食谱
4. 对话式交互（DeepSeek API + Function Calling）

**不做的事**：
- ❌ 不记录饮食（diet-service 已做）
- ❌ 不计算食物热量（diet-service 已做）
- ✅ 只读取 diet-service 的数据进行规划

---

## 二、系统架构

```
前端（对话页面）
    ↓ HTTP
recipe-service (8085)
    ├─ ChatController          → 对话接口
    ├─ ChatService             → 对话流程管理
    ├─ DeepSeekClient          → DeepSeek API
    └─ ToolExecutor            → 工具执行
        ├─ NutritionTool       → 营养计算（BMR/TDEE/分配）
        ├─ RecipeSearchTool    → 搜索食谱
        ├─ RecipeDetailTool    → 食谱详情
        ├─ PreferenceTool      → 用户偏好
        └─ DietClient          → 调用 diet-service
    ↓                    ↓
MySQL              diet-service (8084)
(食谱数据)         (饮食记录)
```

---

## 三、Tools 定义（精简版）

### Tool 1: calculate_nutrition_plan（计算营养计划）

```json
{
  "type": "function",
  "function": {
    "name": "calculate_nutrition_plan",
    "description": "根据用户信息计算每日营养需求和指定餐次的分配",
    "parameters": {
      "type": "object",
      "properties": {
        "meal_type": {
          "type": "string",
          "enum": ["breakfast", "lunch", "dinner", "snack"],
          "description": "餐次类型"
        }
      },
      "required": ["meal_type"]
    }
  }
}

// 返回示例
{
  "user_info": {
    "height": 170,
    "weight": 70,
    "age": 25,
    "gender": "男",
    "activity_level": "moderate",
    "health_goal": "lose_weight"
  },
  "daily_target": {
    "bmr": 1650,
    "tdee": 2558,
    "calories": 2058,
    "protein": 206,
    "carbs": 154,
    "fat": 68
  },
  "meal_target": {
    "calories": 617,
    "protein": 62,
    "carbs": 46,
    "fat": 20
  }
}
```

### Tool 2: get_today_intake（获取今日摄入）

```json
{
  "type": "function",
  "function": {
    "name": "get_today_intake",
    "description": "获取用户今日已摄入的热量和营养素（从 diet-service 读取）",
    "parameters": {
      "type": "object",
      "properties": {}
    }
  }
}

// 返回示例
{
  "date": "2026-06-30",
  "total_calories": 850,
  "entries": [
    {
      "meal_type": "早餐",
      "ingredient_name": "鸡蛋",
      "grams": 100,
      "calories": 144
    },
    {
      "meal_type": "午餐",
      "ingredient_name": "米饭",
      "grams": 200,
      "calories": 232
    }
  ],
  "remaining_calories": 1208
}
```

### Tool 3: search_recipes（搜索食谱）

```json
{
  "type": "function",
  "function": {
    "name": "search_recipes",
    "description": "根据条件搜索食谱，支持营养约束",
    "parameters": {
      "type": "object",
      "properties": {
        "cuisine": {
          "type": "string",
          "description": "菜系类型"
        },
        "taste": {
          "type": "string",
          "description": "口味偏好"
        },
        "ingredients": {
          "type": "array",
          "items": {"type": "string"},
          "description": "可用食材列表"
        },
        "max_calories": {
          "type": "integer",
          "description": "最大热量(kcal)"
        },
        "min_protein": {
          "type": "integer",
          "description": "最小蛋白质(g)"
        },
        "max_cooking_time": {
          "type": "integer",
          "description": "最大烹饪时间(分钟)"
        }
      }
    }
  }
}
```

### Tool 4: get_recipe_detail（获取食谱详情）

```json
{
  "type": "function",
  "function": {
    "name": "get_recipe_detail",
    "description": "获取食谱的详细信息，包括食材清单、营养分析和烹饪步骤",
    "parameters": {
      "type": "object",
      "properties": {
        "recipe_id": {
          "type": "integer",
          "description": "食谱ID"
        }
      },
      "required": ["recipe_id"]
    }
  }
}
```

### Tool 5: save_user_preference（保存用户偏好）

```json
{
  "type": "function",
  "function": {
    "name": "save_user_preference",
    "description": "保存用户的饮食偏好或忌口",
    "parameters": {
      "type": "object",
      "properties": {
        "preference_type": {
          "type": "string",
          "enum": ["like_taste", "dislike_taste", "like_ingredient", "dislike_ingredient", "allergy"],
          "description": "偏好类型"
        },
        "value": {
          "type": "string",
          "description": "偏好值"
        }
      },
      "required": ["preference_type", "value"]
    }
  }
}
```

### Tool 6: get_user_preference（获取用户偏好）

```json
{
  "type": "function",
  "function": {
    "name": "get_user_preference",
    "description": "获取用户的历史饮食偏好和忌口",
    "parameters": {
      "type": "object",
      "properties": {}
    }
  }
}
```

---

## 四、数据库设计

### 4.1 保留的表

- `recipe` - 食谱表
- `recipe_ingredient` - 食谱-食材关联
- `user_preference` - 用户偏好
- `chat_session` - 对话会话
- `chat_message` - 对话消息

### 4.2 不创建的表

- ❌ `user_diet_log` - 饮食记录（diet-service 已有 `diet_log`）

### 4.3 扩展 user 表

```sql
ALTER TABLE user ADD COLUMN activity_level VARCHAR(20) COMMENT '活动量：sedentary/light/moderate/active';
ALTER TABLE user ADD COLUMN health_goal VARCHAR(20) COMMENT '健康目标：lose_weight/maintain/gain_muscle';
```

---

## 五、代码结构

```
recipe-service/
├── src/main/java/com/springboot/intellrecipe/recipe/
│   ├── RecipeApplication.java
│   ├── config/
│   │   └── WebMvcConfig.java
│   ├── controller/
│   │   └── ChatController.java
│   ├── service/
│   │   ├── ChatService.java
│   │   └── impl/
│   │       └── ChatServiceImpl.java
│   ├── client/
│   │   ├── DeepSeekClient.java
│   │   └── DietClient.java              # 调用 diet-service
│   ├── tool/
│   │   ├── ToolExecutor.java
│   │   ├── NutritionTool.java           # 营养计算
│   │   ├── RecipeSearchTool.java
│   │   ├── RecipeDetailTool.java
│   │   └── PreferenceTool.java
│   ├── entity/
│   │   ├── Recipe.java
│   │   ├── RecipeIngredient.java
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   └── UserPreference.java
│   ├── mapper/
│   │   ├── RecipeMapper.java
│   │   ├── RecipeIngredientMapper.java
│   │   ├── ChatSessionMapper.java
│   │   ├── ChatMessageMapper.java
│   │   └── UserPreferenceMapper.java
│   └── dto/
│       ├── ChatRequest.java
│       ├── ChatResponse.java
│       └── ToolCall.java
└── src/main/resources/
    └── application.yml
```

---

## 六、核心流程

### 6.1 新用户引导

```
用户首次进入
    ↓
Agent 打招呼
    ↓
收集基本信息（身高/体重/年龄/性别）
    ↓
保存到 user 表
    ↓
询问活动量和目标
    ↓
保存到 user 表
    ↓
询问忌口
    ↓
保存到 user_preference 表
    ↓
计算营养计划
    ↓
开始正常对话
```

### 6.2 推荐食谱流程

```
用户："今晚吃什么？"
    ↓
Agent 调用 calculate_nutrition_plan(meal_type="dinner")
    → 返回晚餐目标：617kcal，蛋白质62g
    ↓
Agent 调用 get_today_intake()
    → 返回今日已摄入：850kcal
    → 剩余预算：1208kcal
    ↓
Agent 调用 search_recipes(max_calories=617, min_protein=62)
    → 返回符合条件的食谱列表
    ↓
Agent 生成回复：
"根据你的目标，晚餐建议 617kcal 左右。
你今天已摄入 850kcal，还剩 1208kcal 预算。

推荐：
🥗 鸡胸肉沙拉（480kcal，蛋白质42g）
   ✅ 符合晚餐目标

要看看做法吗？"
```

---

## 七、实施步骤（精简版）

### Phase 1：数据库准备（0.5天）

- [ ] 扩展 user 表（activity_level、health_goal）
- [ ] 创建 chat_session 表
- [ ] 创建 chat_message 表
- [ ] 确认 recipe 和 recipe_ingredient 表数据完整

### Phase 2：清理旧代码（0.5天）

- [ ] 删除 PreferenceAnalyzer.java
- [ ] 删除 NutritionTool.java（旧的）
- [ ] 删除 RecipeAgent.java
- [ ] 删除 RecipeController.java

### Phase 3：DeepSeek API 集成（1天）

- [ ] 创建 DeepSeekClient.java
- [ ] 实现 chat 方法（支持 function calling）
- [ ] 配置 application.yml

### Phase 4：DietClient（0.5天）

- [ ] 创建 DietClient.java
- [ ] 实现 getTodayIntake() 方法（调用 diet-service）
- [ ] 配置 Feign 或 RestTemplate

### Phase 5：工具实现（1.5天）

- [ ] 实现 NutritionTool（BMR/TDEE 计算）
- [ ] 实现 RecipeSearchTool（带营养约束）
- [ ] 实现 RecipeDetailTool
- [ ] 实现 PreferenceTool

### Phase 6：对话服务（1.5天）

- [ ] 实现 ChatService（核心对话流程）
- [ ] 实现新用户引导流程
- [ ] 实现工具调用循环
- [ ] 保存对话历史

### Phase 7：Controller 层（0.5天）

- [ ] 实现 ChatController
- [ ] 实现 /send 接口
- [ ] 实现 /sessions 接口

### Phase 8：前端对话页面（2天）

- [ ] 创建 chat.html
- [ ] 实现消息发送和接收
- [ ] 实现食谱卡片渲染
- [ ] 响应式设计

### Phase 9：测试与优化（1天）

- [ ] 端到端测试
- [ ] 优化 System Prompt
- [ ] 部署到云服务器

**总计：9天**

---

## 八、关键问题与解决方案

### 8.1 如何调用 diet-service？

**方案**：使用 RestTemplate 或 Feign

```java
@Component
public class DietClient {
    
    @Value("${diet-service.url:http://localhost:8084}")
    private String dietServiceUrl;
    
    public TodayDietVO getTodayIntake(Long userId, String token) {
        // GET /diet/today
        // Headers: Authorization: {token}
        // 返回 TodayDietVO
    }
}
```

### 8.2 营养计算在哪里做？

**方案**：在 recipe-service 的 NutritionTool 中做

```java
@Component
public class NutritionTool {
    
    public NutritionPlan calculate(Long userId, String mealType) {
        User user = getUser(userId);
        
        // 1. 计算 BMR
        double bmr = calculateBMR(user);
        
        // 2. 计算 TDEE
        double tdee = bmr * getActivityMultiplier(user.getActivityLevel());
        
        // 3. 根据目标调整
        double targetCalories = adjustByGoal(tdee, user.getHealthGoal());
        
        // 4. 分配营养素
        Macronutrients macros = calculateMacros(targetCalories, user.getHealthGoal());
        
        // 5. 分配到餐次
        MealTarget mealTarget = allocateToMeal(macros, mealType);
        
        return new NutritionPlan(bmr, tdee, targetCalories, macros, mealTarget);
    }
}
```

---


---

## 九、当前实现状态分析

### 9.1 架构概览

```
用户请求 → ChatController → ChatService → DeepSeek API
                              ↓
                         ToolExecutor → 具体工具
                              ↓
                         数据库操作（recipe/user_preference）
```

### 9.2 核心流程

**1. 用户发送消息**
```
POST /recipe/chat/send
{
  "sessionId": 123,  // 可选，为空则创建新会话
  "message": "我想吃辣的菜"
}
```

**2. ChatService 处理逻辑**
```java
// 1. 创建/获取会话
if (sessionId == null) {
    sessionId = createSession(userId);  // 新建会话
}

// 2. 保存用户消息到数据库
saveMessage(sessionId, "user", message, null, null);

// 3. 构建对话历史（从数据库加载）
messages = buildMessages(sessionId);
// 返回格式：[
//   {role: "system", content: "你是智能食谱推荐助手..."},
//   {role: "user", content: "我想吃辣的菜"},
//   ...历史消息
// ]

// 4. 获取工具定义
tools = toolExecutor.getToolsDefinition();
// 返回：[
//   {name: "search_recipes", description: "...", parameters: {...}},
//   {name: "get_recipe_detail", ...},
//   {name: "manage_preference", ...}
// ]

// 5. 调用 DeepSeek API
response = deepSeekClient.chat(messages, tools);

// 6. 处理工具调用（循环最多5次）
while (hasToolCalls(response) && iteration < 5) {
    // 解析 tool_calls
    toolCalls = response.tool_calls;
    
    // 执行每个工具
    for (toolCall in toolCalls) {
        result = toolExecutor.execute(toolName, arguments, userId);
        // 例如：search_recipes(taste="辣") → 返回食谱列表
    }
    
    // 将工具结果加入消息历史
    messages = buildMessagesWithToolResults(sessionId, toolResults);
    
    // 再次调用 DeepSeek（带上工具结果）
    response = deepSeekClient.chat(messages, tools);
}

// 7. 保存最终回复
saveMessage(sessionId, "assistant", finalContent, null, null);

// 8. 返回给用户
return ChatResponse(sessionId, finalContent);
```

**3. 工具调用示例**

用户："我想吃辣的菜"

```
第1轮调用 DeepSeek：
→ messages: [system, user:"我想吃辣的菜"]
→ tools: [search_recipes, get_recipe_detail, manage_preference]

DeepSeek 返回：
{
  "content": null,
  "tool_calls": [{
    "name": "search_recipes",
    "arguments": {"taste": "辣", "limit": 5}
  }]
}

执行工具：
→ RecipeSearchTool.execute({"taste": "辣", "limit": 5})
→ 查询数据库：SELECT * FROM recipe WHERE taste_profile LIKE '%辣%' LIMIT 5
→ 返回：[
    {id: 1, name: "麻婆豆腐", calories: 350, ...},
    {id: 2, name: "辣子鸡", calories: 420, ...}
  ]

第2轮调用 DeepSeek：
→ messages: [system, user, assistant(tool_calls), tool(结果)]

DeepSeek 返回：
{
  "content": "给你推荐几道辣菜：\n1. 麻婆豆腐（350卡）...\n2. 辣子鸡（420卡）..."
}

保存并返回给用户
```

### 9.3 数据库表结构

```sql
-- 会话表
chat_session:
  id, user_id, title, create_time, update_time

-- 消息表
chat_message:
  id, session_id, role, content, tool_calls, tool_call_id, create_time
  -- role: user/assistant/system/tool
  -- tool_calls: JSON，存储工具调用信息
  -- tool_call_id: 工具结果对应的调用ID
```

### 9.4 已实现的工具

**1. search_recipes** - 搜索食谱
```json
{
  "name": "search_recipes",
  "parameters": {
    "cuisine": "菜系（川菜/粤菜等）",
    "taste": "口味（辣/清淡等）",
    "max_calories": "最大热量",
    "min_protein": "最小蛋白质"
  }
}
```

**2. get_recipe_detail** - 获取食谱详情
```json
{
  "name": "get_recipe_detail",
  "parameters": {
    "recipe_id": "食谱ID"
  }
}
```

**3. manage_preference** - 管理用户偏好
```json
{
  "name": "manage_preference",
  "parameters": {
    "action": "get/save",
    "preference_type": "like_taste/dislike_taste/like_ingredient/dislike_ingredient",
    "value": "具体值"
  }
}
```

### 9.5 当前存在的问题

**1. 缺少用户信息获取**
- ❌ 没有获取用户身高、体重、年龄、性别等信息
- ❌ 无法计算 BMR/TDEE 和营养目标
- ❌ 无法进行精细化的营养规划

**2. 缺少饮食记录集成**
- ❌ 没有调用 diet-service 获取今日摄入
- ❌ 无法计算剩余热量预算
- ❌ 无法给出"你今天还能吃多少"的建议

**3. 工具功能有限**
- ⚠️ search_recipes 只支持简单的口味/菜系搜索
- ❌ 没有营养约束（max_calories/min_protein 参数未实现）
- ❌ 没有食材匹配（用户说"冰箱有豆腐"，无法匹配含豆腐的食谱）

**4. 缺少新用户引导**
- ❌ 没有检测是否新用户
- ❌ 没有主动询问基本信息和偏好
- ❌ 没有打招呼流程

**5. System Prompt 过于简单**
- ❌ 当前只有基础的角色定义
- ❌ 缺少详细的对话规范
- ❌ 缺少营养计算指导

### 9.6 后续优化方向

**Phase 1：扩展用户信息**
```sql
ALTER TABLE user ADD COLUMN (
  height INT,           -- 身高cm
  weight DECIMAL(5,1),  -- 体重kg
  age INT,              -- 年龄
  gender TINYINT,       -- 性别
  activity_level VARCHAR(20),  -- 活动量
  health_goal VARCHAR(20)      -- 健康目标
);
```

**Phase 2：新增营养计算工具**
```java
// NutritionTool
{
  "name": "calculate_nutrition_plan",
  "parameters": {"meal_type": "breakfast/lunch/dinner"}
}
// 返回：BMR、TDEE、每餐热量目标、营养素分配
```

**Phase 3：集成 diet-service**
```java
// DietClient
GET http://diet-service:8084/diet/today
// 返回：今日已摄入热量、各餐详情
```

**Phase 4：增强 System Prompt**
```
你是"小厨"，专业营养顾问。

新用户引导流程：
1. 打招呼
2. 询问基本信息（身高/体重/年龄/性别）
3. 询问活动量和目标
4. 询问忌口
5. 计算营养计划

老用户对话：
1. 个性化打招呼（显示剩余营养预算）
2. 根据需求推荐食谱
...
```

### 9.7 总结

**当前实现了一个基础的对话式 Agent 框架**：
- ✅ 多轮对话支持
- ✅ 工具调用机制
- ✅ 对话历史持久化
- ✅ 3 个基础工具

**但缺少核心的营养规划能力**：
- ❌ 用户信息收集
- ❌ 营养计算
- ❌ 饮食记录集成
- ❌ 新用户引导
- ❌ 精细化推荐

**下一步需要补充这些功能，才能真正成为"专业营养顾问"。**
**文档版本**：v5.0（复用 diet-service）  
**最后更新**：2026-06-30  
**负责人**：开发团队
