"""工具注册中心：统一挂载、schema 导出（排序稳定，利于 prompt cache）、执行与错误包装。"""
from __future__ import annotations

import json
from typing import Any

from app.tools.base import Tool
from app.tools.base_ctx import ToolContext

MAX_TOOL_RESULT_CHARS = 3000


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, Tool] = {}

    def register(self, tool: Tool) -> None:
        if tool.name in self._tools:
            raise ValueError(f"工具重名: {tool.name}")
        self._tools[tool.name] = tool

    def get(self, name: str) -> Tool | None:
        return self._tools.get(name)

    def names(self) -> list[str]:
        return sorted(self._tools.keys())

    def schemas(self) -> list[dict[str, Any]]:
        """按名称排序导出 OpenAI function schemas（稳定顺序利于 provider 侧缓存）。"""
        return [self._tools[name].to_schema() for name in sorted(self._tools)]

    async def execute(self, name: str, args: dict[str, Any], ctx: ToolContext) -> str:
        """执行工具并把结果包装成 LLM 可读的 JSON 字符串（截断超长结果）。"""
        tool = self._tools.get(name)
        if tool is None:
            return json.dumps({"ok": False, "error": f"未知工具: {name}"}, ensure_ascii=False)
        try:
            data = await tool.execute(ctx, **args)
        except Exception as e:  # 工具异常不中断循环，作为工具结果反馈给 LLM 自行决策
            data = {"ok": False, "error": f"{type(e).__name__}: {e}"}
        if not isinstance(data, dict):
            data = {"ok": True, "data": data}
        text = json.dumps(data, ensure_ascii=False, default=str)
        if len(text) > MAX_TOOL_RESULT_CHARS:
            text = text[:MAX_TOOL_RESULT_CHARS] + f'..."(结果过长已截断, 原始长度 {len(text)})"'
        return text


def parse_tool_args(raw: str) -> dict[str, Any]:
    """解析 LLM 生成的 arguments JSON；非法时抛 ValueError，由调用方包装为错误结果。"""
    raw = (raw or "").strip()
    if not raw:
        return {}
    return json.loads(raw)
