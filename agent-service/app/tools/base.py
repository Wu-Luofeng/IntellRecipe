"""Tool 抽象基类：借鉴 Nanobot 的 Tool/Registry 设计，每个工具自带 OpenAI function schema。"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, ClassVar

from app.tools.base_ctx import ToolContext


class Tool(ABC):
    """所有工具的基类。

    子类必须声明：
      name:          英文短名（LLM function calling 用）
      description:   给 LLM 看的功能描述（写清何时该用本工具）
      parameters:    JSON Schema（OpenAI function 格式的 parameters）
    """

    name: ClassVar[str] = ""
    description: ClassVar[str] = ""
    parameters: ClassVar[dict[str, Any]] = {"type": "object", "properties": {}}

    #: 是否参与"同参数重复调用缓存"（loop 的 memo 防打转机制）。
    #: 读类工具默认 True——LLM 原地打转时防重复执行；
    #: 需要以相同参数反复轮询的工具（如轮询任务状态）应设为 False，每次调用都真执行。
    cacheable: ClassVar[bool] = True

    @abstractmethod
    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        """执行工具。返回 dict；由 Registry 统一包装为 {"ok": bool, ...} 供 LLM 阅读。"""

    def to_schema(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }
