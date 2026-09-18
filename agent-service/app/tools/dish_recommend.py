"""菜品推荐工具（API 版）：数据全部来自 Java 接口，Python 只做分析打分。

数据来源（路径在 config.yaml 的 api.endpoints 配置，Java 侧按契约实现）：
  - user_profile / user_preference：用户身体数据与偏好（配合 token 鉴权）
  - recipes_search：菜品条件查询（keyword/cuisine/taste/maxCalories/minProtein/limit）

打分逻辑（Python 侧，SQL/接口只做粗筛）：
  热量贴近单餐预算 + 菜系偏好匹配 + 口味匹配 - 忌口食材硬过滤
推荐结果全部来自接口返回，绝不编造。
"""
from __future__ import annotations

import re
from typing import Any, ClassVar

from app.core.api_client import ApiClient, field
from app.tools.base import Tool
from app.tools.base_ctx import ToolContext

MEAL_RATIO = {"breakfast": 0.30, "lunch": 0.40, "dinner": 0.35, "snack": 0.10}


def _split_pref(value: Any) -> list[str]:
    """偏好字段形如 "川菜,粤菜" / "清淡、微辣"，拆成词表。"""
    if not value:
        return []
    return [v.strip() for v in re.split(r"[,，、;；/|]+", str(value)) if v.strip()]


class RecommendDishesTool(Tool):
    name = "recommend_dishes"
    description = (
        "根据当前用户的身体数据、口味/菜系偏好、忌口和热量目标，推荐系统里真实存在的菜品。"
        "用户想要'推荐吃什么/今晚吃什么/帮我搭配'时使用。会自动携带用户身份，无需传用户ID。"
    )
    parameters: ClassVar[dict] = {
        "type": "object",
        "properties": {
            "meal_type": {
                "type": "string",
                "enum": ["breakfast", "lunch", "dinner", "snack"],
                "description": "餐次，影响单餐热量预算；不知道就传 dinner",
            },
            "limit": {"type": "integer", "description": "推荐数量，默认 5，最大 8"},
        },
        "required": [],
    }

    def __init__(self, client: ApiClient, endpoints: dict[str, str]):
        self._client = client
        self._endpoints = endpoints

    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        if not ctx.user_id and not ctx.authorization:
            return {"ok": False, "error": "用户未登录，无法按个人数据推荐；可用 search_dishes 按条件搜索"}

        limit = max(1, min(int(kwargs.get("limit") or 5), 8))
        meal = str(kwargs.get("meal_type") or "dinner").lower()

        # 1. 用户数据（接口失败则降级为默认目标，不中断推荐）
        profile = await self._client.get_json(ctx, self._endpoints["user_profile"])
        pref = await self._client.get_json(ctx, self._endpoints["user_preference"])
        profile_data = profile.get("data") or {} if profile.get("ok") else {}
        pref_data = pref.get("data") or {} if pref.get("ok") else {}

        # 2. 单餐热量预算：优先用户设定的每日目标，否则按 Mifflin-St Jeor TDEE 估算
        daily_target = None
        if pref_data.get("dailyCalorieTarget") or pref_data.get("daily_calorie_target"):
            try:
                daily_target = float(field(pref_data, "dailyCalorieTarget", "daily_calorie_target"))
            except (TypeError, ValueError):
                daily_target = None
        h = field(profile_data, "height"); w = field(profile_data, "weight")
        if not daily_target and h and w:
            age = float(field(profile_data, "age", default=30))
            gender = 1 if str(field(profile_data, "gender", default=0)) in ("1", "male") else 0
            bmr = 10 * float(w) + 6.25 * float(h) - 5 * age + (5 if gender else -161)
            daily_target = bmr * 1.375
        if not daily_target:
            daily_target = 2000.0
        meal_budget = daily_target * MEAL_RATIO.get(meal, 0.35)

        # 3. 接口粗筛：热量落在单餐预算 40%~160% 区间的上架菜品
        resp = await self._client.get_json(ctx, self._endpoints["recipes_search"], {
            "minCalories": round(meal_budget * 0.4),
            "maxCalories": round(meal_budget * 1.6),
            "limit": 60,
        })
        if not resp.get("ok"):
            return {"ok": False,
                    "error": f"菜品查询接口不可用：{resp.get('error', resp.get('status'))}；"
                             f"请确认 Java 侧已实现该接口（当前路径 {self._endpoints['recipes_search']}）"}
        rows = ApiClient.as_list(resp.get("data"))

        avoid_tokens = _split_pref(field(pref_data, "avoidIngredients", "avoid_ingredients"))
        wanted_cuisines = _split_pref(field(pref_data, "cuisinePreference", "cuisine_preference"))
        wanted_tastes = _split_pref(field(pref_data, "tastePreference", "taste_preference"))
        health_goal = str(field(pref_data, "healthGoal", "health_goal") or "")

        scored: list[tuple[float, dict]] = []
        for row in rows:
            text = f"{field(row, 'name') or ''}{field(row, 'description') or ''}"
            if any(tok and tok in text for tok in avoid_tokens):
                continue  # 忌口硬过滤
            score = 0.0
            reasons: list[str] = []
            cuisine = str(field(row, "cuisineType", "cuisine_type") or "")
            taste = str(field(row, "tasteProfile", "taste_profile") or "")
            calories = float(field(row, "calories", default=0))
            if any(c and c in cuisine for c in wanted_cuisines):
                score += 3.0
                reasons.append(f"符合你偏好的{satisfy(wanted_cuisines, cuisine)}菜系")
            taste_hits = [t for t in wanted_tastes if t and t in taste]
            if taste_hits:
                score += 2.0
                reasons.append("口味对得上你的偏好（" + "、".join(taste_hits) + "）")
            score += max(0.0, 2.0 - abs(calories - meal_budget) / (meal_budget * 0.6))
            if "减" in health_goal and calories <= meal_budget:
                reasons.append("热量符合减脂目标")
            protein = float(field(row, "protein", default=0))
            if protein >= 20:
                score += 1.0
                reasons.append("高蛋白")
            if not reasons:
                reasons.append(f"热量 {calories:.0f} kcal，接近你本餐 {meal_budget:.0f} kcal 的预算")
            scored.append((score, {
                "id": field(row, "id"), "name": field(row, "name"),
                "cuisine": cuisine, "taste": taste,
                "calories_kcal": round(calories, 1),
                "protein_g": round(protein, 1),
                "fat_g": round(float(field(row, "fat", default=0)), 1),
                "carbs_g": round(float(field(row, "carbs", default=0)), 1),
                "reason": "；".join(reasons[:2]),
            }))

        scored.sort(key=lambda x: x[0], reverse=True)
        return {
            "ok": True,
            "meal": meal,
            "meal_calorie_budget": round(meal_budget),
            "daily_calorie_target": round(daily_target),
            "count": min(limit, len(scored)),
            "dishes": [d for _, d in scored[:limit]],
        }


def satisfy(wanted: list[str], actual: str) -> str:
    for w in wanted:
        if w and w in actual:
            return w
    return actual
