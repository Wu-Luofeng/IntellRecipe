"""LLM Provider 抽象：借鉴 Nanobot——所有厂商差异收敛到 chat()/chat_stream() 两个接口。

当前实现 OpenAICompatProvider：任何 OpenAI 兼容接口（DeepSeek/通义/vLLM/one-api）。
内置 429/5xx/网络错误的指数退避重试（流式仅在首块前重试，避免重复输出）。
"""
from __future__ import annotations

import asyncio
import json
from abc import ABC, abstractmethod
from typing import Any, AsyncIterator

import httpx


class LLMProvider(ABC):
    @abstractmethod
    async def chat(self, messages: list[dict], tools: list[dict] | None = None) -> dict[str, Any]:
        """输入 OpenAI 消息列表（可带 tools schemas），返回 assistant message dict：
        {"role": "assistant", "content": str|null, "tool_calls": [...]|缺失}
        """

    @abstractmethod
    def chat_stream(self, messages: list[dict], tools: list[dict] | None = None) -> AsyncIterator[dict]:
        """流式版本：逐个 yield OpenAI 流式 chunk（choices[0].delta / finish_reason）。"""


class OpenAICompatProvider(LLMProvider):
    RETRYABLE_STATUS = {429, 500, 502, 503, 504}
    MAX_RETRIES = 3

    def __init__(self, base_url: str, api_key: str, model: str,
                 temperature: float = 0.6, max_tokens: int = 2048):
        self._model = model
        self._temperature = temperature
        self._max_tokens = max_tokens
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            headers={"Authorization": f"Bearer {api_key}"},
            timeout=60.0,
        )

    async def chat(self, messages: list[dict], tools: list[dict] | None = None) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": messages,
            "temperature": self._temperature,
            "max_tokens": self._max_tokens,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        last_err: Exception | None = None
        for attempt in range(self.MAX_RETRIES):
            try:
                resp = await self._client.post("/chat/completions", json=payload)
                if resp.status_code in self.RETRYABLE_STATUS:
                    last_err = RuntimeError(f"LLM HTTP {resp.status_code}: {resp.text[:200]}")
                else:
                    resp.raise_for_status()
                    return resp.json()["choices"][0]["message"]
            except httpx.RequestError as e:  # 网络类错误：可重试
                last_err = e
            await asyncio.sleep(0.5 * (2 ** attempt))
        raise RuntimeError(f"LLM 调用失败（重试 {self.MAX_RETRIES} 次）: {last_err}")

    async def chat_stream(self, messages: list[dict], tools: list[dict] | None = None) -> AsyncIterator[dict]:
        """SSE 流式：yield 原始 chunk dict。重试仅覆盖「首块到达前」的失败，
        已开始输出后的中断直接抛出（上层以此保证不重复输出）。"""
        payload: dict[str, Any] = {
            "model": self._model,
            "messages": messages,
            "temperature": self._temperature,
            "max_tokens": self._max_tokens,
            "stream": True,
        }
        if tools:
            payload["tools"] = tools
            payload["tool_choice"] = "auto"

        last_err: Exception | None = None
        for attempt in range(self.MAX_RETRIES):
            started = False
            try:
                async with self._client.stream("POST", "/chat/completions", json=payload) as resp:
                    if resp.status_code in self.RETRYABLE_STATUS:
                        raw = (await resp.aread()).decode("utf-8", "replace")
                        last_err = RuntimeError(f"LLM HTTP {resp.status_code}: {raw[:200]}")
                        continue  # 首块未出，可安全重试
                    resp.raise_for_status()
                    async for line in resp.aiter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if not data or data == "[DONE]":
                            if data == "[DONE]":
                                return
                            continue
                        started = True
                        yield json.loads(data)
                    return
            except httpx.RequestError as e:
                if started:  # 流已输出过内容：不能重试，直接失败
                    raise RuntimeError(f"LLM 流式输出中断: {e}")
                last_err = e
            except RuntimeError:
                if started:
                    raise
            await asyncio.sleep(0.5 * (2 ** attempt))
        raise RuntimeError(f"LLM 流式调用失败（重试 {self.MAX_RETRIES} 次）: {last_err}")

    async def close(self) -> None:
        await self._client.aclose()
