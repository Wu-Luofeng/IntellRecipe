"""通用动态 API 调用工具：LLM 决定调哪个接口，服务负责安全边界（白名单在 ApiClient 集中实现）。"""
from __future__ import annotations

from typing import Any, ClassVar

from app.core.api_client import ApiClient
from app.tools.base import Tool
from app.tools.base_ctx import ToolContext

KNOWN_ENDPOINTS = """IntellRecipe 网关上的常用接口（白名单内）：
- GET  /user/me                 当前用户信息（需用户 token）
- GET  /diet/today              当前用户今日饮食摄入（需用户 token）
- GET  /ingredient/list         食材列表
- GET  /items/list              商品列表
更多接口以实际服务为准；调用失败会返回错误信息。"""


class CallApiTool(Tool):
    name = "call_api"
    description = (
        "动态调用 IntellRecipe 业务系统网关上的 HTTP 接口（GET/POST）。"
        "当其他工具覆盖不了的需求（查食材、查订单、查商品等）时使用。" + KNOWN_ENDPOINTS
    )
    parameters: ClassVar[dict] = {
        "type": "object",
        "properties": {
            "method": {"type": "string", "enum": ["GET", "POST"], "description": "HTTP 方法"},
            "path": {"type": "string", "description": "接口路径，以 / 开头，如 /ingredient/list"},
            "query": {
                "type": "object",
                "description": "查询参数（GET）或 JSON 请求体（POST）",
                "additionalProperties": {"type": ["string", "number", "boolean"]},
            },
        },
        "required": ["method", "path"],
    }

    def __init__(self, client: ApiClient):
        self._client = client

    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        return await self._client.request(
            ctx, str(kwargs.get("method") or "GET"), str(kwargs.get("path") or ""),
            query=kwargs.get("query"))
