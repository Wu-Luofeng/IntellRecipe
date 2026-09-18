"""ReAct 主循环：LLM ↔ 工具迭代，直到给出最终回答或到达迭代上限。

借鉴 Nanobot 的 Runner 思路但保持最小化：
- OpenAI function calling 格式
- max_iterations 上限（防死循环）
- 工具结果截断（Registry 负责）
- 会话裁剪防孤儿 tool 消息（SessionStore 负责）
- run()      ：一次性返回最终回答
- run_stream()：SSE 事件流（delta=回答增量 / tool=工具调用 / done=完成），会话记账与 run() 完全一致
"""
from __future__ import annotations

import json
from typing import Any, AsyncIterator

from app.core.context import HEALTH_DISCLAIMER, build_system_prompt
from app.core.provider import LLMProvider
from app.core.registry import ToolRegistry, parse_tool_args
from app.core.session import SessionStore
from app.tools.base_ctx import ToolContext

FALLBACK_REPLY = "抱歉，我处理这次请求时遇到了问题，请稍后再试。"
MAX_TOOL_RESULT_IN_HISTORY = 2000
HEALTH_TOOLS = {"recommend_dishes", "search_dishes", "get_user_context", "calc_health_metrics"}


class AgentLoop:
    def __init__(self, provider: LLMProvider, registry: ToolRegistry,
                 sessions: SessionStore, max_iterations: int = 6):
        self._provider = provider
        self._registry = registry
        self._sessions = sessions
        self._max_iterations = max_iterations

    async def run(self, session_id: str, message: str, ctx: ToolContext) -> dict[str, Any]:
        """处理一条用户消息，返回 {"reply": 最终回答, "tool_trace": 本轮工具调用轨迹}。"""
        history = self._sessions.history(session_id)
        history.append({"role": "user", "content": message})

        system = {"role": "system", "content": build_system_prompt(ctx.user_id)}
        tools = self._registry.schemas()
        trace: list[dict[str, Any]] = []
        health_related = False
        # 重复调用防御（作用域 = 本次用户消息）：同工具+同参数 → 直接复用结果，防 LLM 原地打转。
        # 工具可通过 cacheable=False 退出此机制（如需要以相同参数轮询的工具）。
        memo: dict[str, str] = {}

        def memo_lookup(name: str, args: dict) -> tuple[bool, str | None]:
            """返回 (是否命中缓存, 缓存结果)。cacheable=False 的工具永不命中。"""
            tool = self._registry.get(name)
            if tool is not None and not tool.cacheable:
                return False, None
            key = name + ":" + json.dumps(args, sort_keys=True, ensure_ascii=False)
            if key in memo:
                return True, memo[key]
            return False, key   # 未命中时把 key 带回，调用方执行后回填

        for _ in range(self._max_iterations):
            assistant = await self._provider.chat([system] + history, tools)

            tool_calls = assistant.get("tool_calls") or []
            if not tool_calls:
                content = (assistant.get("content") or FALLBACK_REPLY).strip()
                history.append({"role": "assistant", "content": content})
                if health_related:
                    content += HEALTH_DISCLAIMER
                return {"reply": content, "tool_trace": trace}

            # 记录 assistant 的工具调用意图
            history.append({
                "role": "assistant",
                "content": assistant.get("content") or "",
                "tool_calls": tool_calls,
            })

            # 逐个执行工具，结果作为 tool 消息回填
            for call in tool_calls:
                fn = call.get("function", {})
                name = fn.get("name", "")
                try:
                    args = parse_tool_args(fn.get("arguments", "{}"))
                except (ValueError, json.JSONDecodeError) as e:
                    result = json.dumps({"ok": False, "error": f"参数解析失败: {e}"}, ensure_ascii=False)
                else:
                    hit, cached_or_key = memo_lookup(name, args)
                    if hit:
                        # LLM 原地打转：相同工具+相同参数已执行过 → 直接复用结果并明确提示
                        result = json.dumps(
                            {"ok": True, "cached": True,
                             "note": "本次对话中已用相同参数调用过该工具，请直接基于以下此前结果作答，不要再次调用",
                             "previous_result": (cached_or_key or "")[:1500]},
                            ensure_ascii=False)
                    else:
                        result = await self._registry.execute(name, args, ctx)
                        if cached_or_key:
                            memo[cached_or_key] = result

                if name in ("recommend_dishes", "search_dishes", "get_user_context", "calc_health_metrics"):
                    health_related = True
                trace.append({"tool": name, "args": args, "result": result[:300]})

                history.append({
                    "role": "tool",
                    "tool_call_id": call.get("id", ""),
                    "content": result[:MAX_TOOL_RESULT_IN_HISTORY],
                })

        # 迭代上限：给用户一个明确交代，而不是静默失败
        reply = ("这个问题我调用了几轮工具还没能整理出可靠答案。"
                 "你可以缩小问题范围再试一次，例如只问某一个方面。")
        history.append({"role": "assistant", "content": reply})
        return {"reply": reply, "tool_trace": trace}

    async def run_stream(self, session_id: str, message: str,
                         ctx: ToolContext) -> AsyncIterator[dict[str, Any]]:
        """流式版 run()：yield 事件序列
        {"type":"delta","content":...}   回答文本增量（仅最终文本轮）
        {"type":"tool","name":...,"result":...}   一个工具执行完成
        {"type":"done","reply":...,"trace":[...]} 完成（reply 含完整最终回答）
        {"type":"error","error":...}      失败
        会话记账（history append / 裁剪）与 run() 完全一致。
        """
        history = self._sessions.history(session_id)
        history.append({"role": "user", "content": message})

        system = {"role": "system", "content": build_system_prompt(ctx.user_id)}
        tools = self._registry.schemas()
        trace: list[dict[str, Any]] = []
        health_related = False
        memo: dict[str, str] = {}   # 同 run()：本次消息内的重复调用防御

        for round_no in range(self._max_iterations):
            if round_no > 0:
                # 新一轮开始：通知前端重置文本缓冲（防止上一轮的思考旁白与最终答案混排）
                yield {"type": "round", "index": round_no}
            content_acc = ""
            tc_acc: dict[int, dict[str, str]] = {}
            try:
                async for chunk in self._provider.chat_stream([system] + history, tools):
                    choice = (chunk.get("choices") or [{}])[0]
                    delta = choice.get("delta") or {}
                    piece = delta.get("content")
                    if piece:
                        content_acc += piece
                        yield {"type": "delta", "content": piece}
                    for tc in delta.get("tool_calls") or []:
                        idx = tc.get("index", 0)
                        acc = tc_acc.setdefault(idx, {"id": "", "name": "", "args": ""})
                        if tc.get("id"):
                            acc["id"] = tc["id"]
                        fn = tc.get("function") or {}
                        if fn.get("name"):
                            acc["name"] += fn["name"]
                        if fn.get("arguments"):
                            acc["args"] += fn["arguments"]
            except Exception as e:
                yield {"type": "error", "error": f"LLM 流式调用失败: {e}"}
                return

            if tc_acc:
                tool_calls = [{"id": acc["id"] or f"call_{i}", "type": "function",
                               "function": {"name": acc["name"], "arguments": acc["args"]}}
                              for i, acc in sorted(tc_acc.items())]
                history.append({"role": "assistant",
                                "content": content_acc, "tool_calls": tool_calls})

                for call in tool_calls:
                    fn = call["function"]
                    name = fn["name"]
                    try:
                        args = parse_tool_args(fn["arguments"])
                    except (ValueError, json.JSONDecodeError) as e:
                        result = json.dumps({"ok": False, "error": f"参数解析失败: {e}"}, ensure_ascii=False)
                    else:
                        hit, cached_or_key = memo_lookup(name, args)
                        if hit:
                            result = json.dumps(
                                {"ok": True, "cached": True,
                                 "note": "本次对话中已用相同参数调用过该工具，请直接基于以下此前结果作答，不要再次调用",
                                 "previous_result": (cached_or_key or "")[:1500]},
                                ensure_ascii=False)
                        else:
                            result = await self._registry.execute(name, args, ctx)
                            if cached_or_key:
                                memo[cached_or_key] = result

                    if name in HEALTH_TOOLS:
                        health_related = True
                    trace.append({"tool": name, "args": args, "result": result[:300]})
                    history.append({"role": "tool", "tool_call_id": call["id"],
                                    "content": result[:MAX_TOOL_RESULT_IN_HISTORY]})
                    yield {"type": "tool", "name": name, "result": result[:400]}
                continue

            # 最终文本轮
            content = (content_acc or FALLBACK_REPLY).strip()
            if health_related:
                content += HEALTH_DISCLAIMER
            history.append({"role": "assistant", "content": content})
            yield {"type": "done", "reply": content, "trace": trace}
            return

        reply = ("这个问题我调用了几轮工具还没能整理出可靠答案。"
                 "你可以缩小问题范围再试一次，例如只问某一个方面。")
        history.append({"role": "assistant", "content": reply})
        yield {"type": "done", "reply": reply, "trace": trace}
