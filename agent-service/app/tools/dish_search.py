"""菜品搜索工具（API 版）：按条件调 Java 侧菜品查询接口。"""
from __future__ import annotations

from typing import Any, ClassVar

from app.core.api_client import ApiClient, field
from app.tools.base import Tool
from app.tools.base_ctx import ToolContext


class SearchDishesTool(Tool):
    name = "search_dishes"
    description = (
        "按条件搜索系统里的菜品（不依赖个人偏好）。"
        "用户给出明确条件时使用，如'找低卡的川菜'、'有没有麻辣口味'、'高蛋白的菜'。"
    )
    parameters: ClassVar[dict] = {
        "type": "object",
        "properties": {
            "keyword": {"type": "string", "description": "菜品名称/描述关键词，如 鸡胸、豆腐"},
            "cuisine": {"type": "string", "description": "菜系，如 川菜、粤菜、家常菜"},
            "taste": {"type": "string", "description": "口味，如 清淡、麻辣、酸甜"},
            "max_calories": {"type": "number", "description": "单份热量上限（kcal）"},
            "min_protein": {"type": "number", "description": "蛋白质下限（g）"},
            "limit": {"type": "integer", "description": "返回数量上限，默认 5，最大 10"},
        },
        "required": [],
    }

    def __init__(self, client: ApiClient, endpoints: dict[str, str]):
        self._client = client
        self._search_path = endpoints["recipes_search"]

    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        limit = max(1, min(int(kwargs.get("limit") or 5), 10))
        query: dict[str, Any] = {"limit": limit}
        if kw := str(kwargs.get("keyword") or "").strip():
            query["keyword"] = kw
        if cuisine := str(kwargs.get("cuisine") or "").strip():
            query["cuisine"] = cuisine
        if taste := str(kwargs.get("taste") or "").strip():
            query["taste"] = taste
        if max_cal := kwargs.get("max_calories"):
            query["maxCalories"] = float(max_cal)
        if min_pro := kwargs.get("min_protein"):
            query["minProtein"] = float(min_pro)

        resp = await self._client.get_json(ctx, self._search_path, query)
        if not resp.get("ok"):
            return {"ok": False,
                    "error": f"菜品查询接口不可用：{resp.get('error', resp.get('status'))}；"
                             f"请确认 Java 侧已实现该接口（当前路径 {self._search_path}）"}

        rows = ApiClient.as_list(resp.get("data"))
        dishes = [{
            "id": field(r, "id"), "name": field(r, "name"),
            "cuisine": field(r, "cuisineType", "cuisine_type"),
            "taste": field(r, "tasteProfile", "taste_profile"),
            "calories_kcal": float(field(r, "calories", default=0)),
            "protein_g": float(field(r, "protein", default=0)),
            "description": str(field(r, "description") or "")[:80],
        } for r in rows[:limit]]
        return {"ok": True, "count": len(dishes), "dishes": dishes}
