"""离线验证 AgentLoop 的两个语义（不调用真实 LLM）：

1. 一轮多个工具：LLM 一次返回 N 个 tool_calls → 内层循环执行 N 次，但只消耗 1 个轮次
2. 重复调用防御：同工具+同参数再次被要求执行 → 命中 memo，返回 cached 结果，不再真执行

用 FakeProvider 按剧本回放，统计 LLM 调用次数与工具真实执行次数。
"""
from __future__ import annotations

import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.loop import AgentLoop
from app.core.provider import LLMProvider
from app.core.registry import ToolRegistry
from app.core.session import SessionStore
from app.tools.base import Tool
from app.tools.base_ctx import ToolContext


class FakeProvider(LLMProvider):
    """按剧本回放：script 里每项是一段 assistant 消息。"""
    def __init__(self, script: list[dict]):
        self.script = script
        self.llm_calls = 0

    async def chat(self, messages, tools=None):
        step = self.script[min(self.llm_calls, len(self.script) - 1)]
        self.llm_calls += 1
        return step

    async def chat_stream(self, messages, tools=None):  # 本测试不覆盖流式路径
        yield {}
        return


class CountingTool(Tool):
    name = "count_ingredients"
    description = "测试用：统计被真实执行的次数"
    parameters = {"type": "object", "properties": {"kw": {"type": "string"}}}

    def __init__(self):
        self.real_executions = 0

    async def execute(self, ctx, **kwargs):
        self.real_executions += 1
        return {"ok": True, "executed": self.real_executions, "kw": kwargs.get("kw")}


class PollingTool(Tool):
    """cacheable=False 的工具：同参数每次调用都应真执行（模拟轮询类工具）。"""
    name = "poll_status"
    description = "测试用：轮询类工具，不参与缓存"
    parameters = {"type": "object", "properties": {"task": {"type": "string"}}}
    cacheable = False

    def __init__(self):
        self.real_executions = 0

    async def execute(self, ctx, **kwargs):
        self.real_executions += 1
        return {"ok": True, "poll": self.real_executions}


def assistant_with_tools(calls: list[dict]) -> dict:
    return {"role": "assistant", "content": "", "tool_calls": calls}


def call(cid: str, kw: str) -> dict:
    return {"id": cid, "type": "function",
            "function": {"name": "count_ingredients",
                         "arguments": json.dumps({"kw": kw})}}


async def main() -> None:
    tool = CountingTool()
    polling = PollingTool()
    registry = ToolRegistry()
    registry.register(tool)
    registry.register(polling)

    # 剧本：
    #   第0轮 并行调 2 个工具（kw=A 与 kw=B）→ 第1轮 又调 kw=A（重复，应命中缓存）
    #        → 第2轮 两次调 poll_status(task=X)（cacheable=False，每次都应真执行）
    #        → 第3轮 纯文本收尾
    script = [
        assistant_with_tools([call("c1", "A"), call("c2", "B")]),
        assistant_with_tools([call("c3", "A")]),          # 与 c1 相同参数 → 应命中 memo
        assistant_with_tools([
            {"id": "p1", "type": "function",
             "function": {"name": "poll_status", "arguments": json.dumps({"task": "X"})}},
            {"id": "p2", "type": "function",
             "function": {"name": "poll_status", "arguments": json.dumps({"task": "X"})}},
        ]),
        {"role": "assistant", "content": "最终回答"},
    ]
    provider = FakeProvider(script)
    loop = AgentLoop(provider, registry, SessionStore(40), max_iterations=6)

    result = await loop.run("test", "开始", ToolContext(user_id=1))

    print("LLM 调用次数（=轮次）:", provider.llm_calls)
    print("count_ingredients 真实执行次数:", tool.real_executions, "（预期 2：A/B 各一次，重复的 A 命中缓存）")
    print("poll_status 真实执行次数:", polling.real_executions, "（预期 2：cacheable=False 每次真执行）")
    print("最终回答:", result["reply"])

    assert provider.llm_calls == 4, "第0轮2个工具应只算1轮"
    assert tool.real_executions == 2, "c3 与 c1 同参数应命中缓存，不再真执行"
    assert polling.real_executions == 2, "cacheable=False 的工具每次都真执行"
    cached = [t for t in result["tool_trace"] if "cached" in t["result"]]
    assert cached and json.loads(cached[0]["result"]).get("cached") is True
    print("== PASS：多工具同轮计数正确；重复调用命中缓存；cacheable=False 不受影响 ==")


if __name__ == "__main__":
    asyncio.run(main())
