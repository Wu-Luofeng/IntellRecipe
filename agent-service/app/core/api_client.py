"""统一 HTTP API 客户端：Agent 访问 Java 侧数据的唯一通道（Python 不直连数据库）。

安全边界（集中在这里）：
  - 域名白名单：只能调用配置的网关 base url；
  - 凭证透传：用户登录 token 从 ToolContext 透传，不进提示词、LLM 不可见；
  - Result 信封解包：Java 侧统一返回 {success, data, errorMsg}，这里统一解包；
  - 超时 + 响应截断。
"""
from __future__ import annotations

from typing import Any

import httpx

from app.tools.base_ctx import ToolContext

LIST_KEYS = ("list", "records", "items", "rows", "data")


class ApiClient:
    def __init__(self, base_url: str, timeout: float = 10.0, max_chars: int = 4000):
        self._base = base_url.rstrip("/")
        self._timeout = timeout
        self._max_chars = max_chars

    async def get_json(self, ctx: ToolContext, path: str,
                       query: dict[str, Any] | None = None) -> dict:
        """GET 并解包 Result 信封。任何失败都返回 {"ok": False, "error": ...}，不抛异常。"""
        return await self.request(ctx, "GET", path, query=query)

    async def request(self, ctx: ToolContext, method: str, path: str,
                      query: dict[str, Any] | None = None,
                      json_body: dict[str, Any] | None = None) -> dict:
        method = method.upper()
        if method not in ("GET", "POST"):
            return {"ok": False, "error": f"不允许的 HTTP 方法: {method}"}
        if not path.startswith("/") or ".." in path or path.startswith("//"):
            return {"ok": False, "error": "非法路径，必须以 / 开头且不得包含路径穿越"}
        url = f"{self._base}{path}"
        if not url.startswith(self._base):  # 双保险：防拼出白名单外的绝对地址
            return {"ok": False, "error": f"目标不在白名单内: {url}"}

        headers: dict[str, str] = {}
        if ctx.authorization:
            headers["authorization"] = ctx.authorization

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.request(
                    method, url,
                    params=query if method == "GET" else None,
                    json=json_body if method == "POST" else None,
                    headers=headers)
        except httpx.TimeoutException:
            return {"ok": False, "error": f"上游接口超时（>{self._timeout}s）：{path}"}
        except httpx.RequestError as e:
            return {"ok": False, "error": f"上游接口不可达：{type(e).__name__}（服务未启动或路径不存在）"}

        text = resp.text
        if len(text) > self._max_chars:
            text = text[: self._max_chars] + f'..."(已截断, 原始长度 {len(resp.text)})"'
        try:
            body = resp.json()
        except ValueError:
            return {"ok": resp.status_code < 400, "status": resp.status_code, "body": text}
        return {"ok": resp.status_code < 400, "status": resp.status_code, **self._unwrap(body)}

    @staticmethod
    def _unwrap(body: Any) -> dict:
        """解 Java Result 信封 {success, data, errorMsg}；非信封结构原样透出。"""
        if isinstance(body, dict) and "success" in body:
            if body.get("success") is True:
                return {"data": body.get("data")}
            return {"ok": False, "error": body.get("errorMsg") or "业务接口返回失败"}
        return {"data": body}

    @staticmethod
    def as_list(data: Any) -> list[Any]:
        """把 data 归一化为列表：直接是列表就用，dict 里找常见列表字段。"""
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            for key in LIST_KEYS:
                if isinstance(data.get(key), list):
                    return data[key]
            for v in data.values():  # 兜底：任意一个列表值
                if isinstance(v, list):
                    return v
        return []


def field(row: dict, *names: str, default: Any = None) -> Any:
    """按多个候选名取字段（兼容 Java 侧 camelCase 与 DB snake_case 两种返回风格）。"""
    for n in names:
        v = row.get(n)
        if v is not None:
            return v
    return default
