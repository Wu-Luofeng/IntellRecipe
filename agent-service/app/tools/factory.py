"""工具装配工厂：把内置工具注册到 Registry（新增工具在此加一行）。

数据源统一为 Java 网关接口（Python 不直连数据库），见 config.yaml 的 api.endpoints。
"""
from __future__ import annotations

from app.config import APIConfig
from app.core.api_client import ApiClient
from app.core.registry import ToolRegistry
from app.tools.dish_recommend import RecommendDishesTool
from app.tools.dish_search import SearchDishesTool
from app.tools.health_calc import CalcHealthMetricsTool
from app.tools.http_api import CallApiTool
from app.tools.user_context import GetUserContextTool


def build_registry(cfg: APIConfig) -> ToolRegistry:
    client = ApiClient(cfg.gateway_base, cfg.timeout_seconds, cfg.max_response_chars)
    registry = ToolRegistry()
    registry.register(RecommendDishesTool(client, cfg.endpoints))
    registry.register(SearchDishesTool(client, cfg.endpoints))
    registry.register(GetUserContextTool(client, cfg.endpoints))
    registry.register(CalcHealthMetricsTool())
    registry.register(CallApiTool(client))
    return registry
