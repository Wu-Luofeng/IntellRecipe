"""用户上下文工具（API 版）：聚合身体数据、偏好、今日摄入（全部来自 Java 接口）。"""
from __future__ import annotations

from typing import Any, ClassVar

from app.core.api_client import ApiClient, field
from app.tools.base import Tool
from app.tools.base_ctx import ToolContext


class GetUserContextTool(Tool):
    name = "get_user_context"
    description = (
        "读取当前用户的身体数据（身高体重年龄性别）、饮食偏好（菜系/口味/忌口/健康目标/每日热量目标）"
        "和今日已记录的饮食摄入摘要。回答个人化健康问题、给出饮食建议前先调用。"
        "自动携带当前用户身份（token 透传），无需传用户ID。"
    )
    parameters: ClassVar[dict] = {"type": "object", "properties": {}}

    def __init__(self, client: ApiClient, endpoints: dict[str, str]):
        self._client = client
        self._ep = endpoints

    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        if not ctx.authorization and not ctx.user_id:
            return {"ok": False, "error": "用户未登录，无个人数据可用"}

        profile = await self._client.get_json(ctx, self._ep["user_profile"])
        pref = await self._client.get_json(ctx, self._ep["user_preference"])
        today = await self._client.get_json(ctx, self._ep["today_diet"])

        # 今日摄入：兼容不同返回结构，尽量提取热量求和
        today_data = today.get("data") if today.get("ok") else None
        items: list[str] = []
        total_kcal = 0.0
        rows = ApiClient.as_list(today_data)
        for r in rows:
            grams = float(field(r, "grams", default=0))
            per100 = float(field(r, "caloriesPer100g", "calories_per_100g", default=0))
            total_kcal += per100 * grams / 100.0
            items.append(f"{field(r, 'ingredientName', 'ingredient_name')} {grams}g")

        failed = [name for name, resp in
                  (("身体数据", profile), ("偏好", pref), ("今日摄入", today)) if not resp.get("ok")]
        return {
            "ok": True,
            "profile": profile.get("data") or {},
            "preference": pref.get("data") or {},
            "today_intake": {"total_kcal": round(total_kcal, 1), "items": items[:10]},
            "unavailable_parts": failed,  # 告诉 LLM 哪部分数据暂时拿不到，自行调整回答
        }
