"""会话记忆：内存版多轮上下文（进程级 dict），裁剪时保证不产生孤儿 tool 消息。

生产化替换点：接口不变，把存储换成 Redis（TTL + 按用户隔离）即可。
"""
from __future__ import annotations


class SessionStore:
    def __init__(self, max_messages: int = 40):
        self._max = max_messages
        self._data: dict[str, list[dict]] = {}

    def history(self, session_id: str) -> list[dict]:
        return self._data.setdefault(session_id, [])

    def dump(self, session_id: str) -> list[dict]:
        """只回放可见内容（user / assistant.text），供页面刷新后重渲染。"""
        out: list[dict] = []
        for m in self.history(session_id):
            role = m.get("role")
            content = (m.get("content") or "").strip()
            if role == "user" and content:
                out.append({"role": "user", "content": content})
            elif role == "assistant" and content:
                out.append({"role": "assistant", "content": content})
        return out

    def append(self, session_id: str, message: dict) -> None:
        self.history(session_id).append(message)
        self._trim(session_id)

    def _trim(self, session_id: str) -> None:
        msgs = self._data[session_id]
        if len(msgs) <= self._max:
            return
        msgs[:] = msgs[-self._max:]
        # 裁剪后首条若是 tool 消息（没有配对的 assistant.tool_calls），丢弃直到合法边界
        i = 0
        while i < len(msgs) and msgs[i]["role"] == "tool":
            i += 1
        if i:
            msgs[:] = msgs[i:]
