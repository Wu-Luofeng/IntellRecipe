"""FastAPI 入口：Agent 服务的对外 HTTP 形态。

POST /agent/chat                    对话（body: session_id?/user_id?/message；header: authorization 可选透传）
GET  /agent/chat/{sid}/history      回放某会话历史（页面刷新后重渲染，上下文不丢）
GET  /agent/tools                   查看已注册工具的 schema（调试用）
GET  /agent/health                  存活检查
GET  /chat                          独立对话页面（浏览器直接打开）

数据源说明：Python 不直连数据库，所有业务数据通过 Java 网关接口获取
（ApiClient 统一负责白名单/信封解包/凭证透传），接口契约见 config.yaml 的 api.endpoints。
"""
from __future__ import annotations

import uuid
import json
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Header
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel, Field

from app.config import load_config
from app.core.loop import AgentLoop
from app.core.provider import OpenAICompatProvider
from app.core.registry import ToolRegistry
from app.core.session import SessionStore
from app.tools.base_ctx import ToolContext
from app.tools.factory import build_registry

STATIC_DIR = Path(__file__).resolve().parent / "static"


@asynccontextmanager
async def lifespan(app: FastAPI):
    cfg = load_config()
    registry: ToolRegistry = build_registry(cfg.api)
    provider = OpenAICompatProvider(
        cfg.llm.base_url, cfg.llm.api_key, cfg.llm.model,
        cfg.llm.temperature, cfg.llm.max_tokens)
    sessions = SessionStore(cfg.agent.history_max_messages)
    app.state.cfg = cfg
    app.state.sessions = sessions
    app.state.loop = AgentLoop(provider, registry, sessions, cfg.agent.max_iterations)
    app.state.registry = registry
    yield
    await provider.close()


app = FastAPI(title="IntellRecipe Agent Service", version="0.2.0", lifespan=lifespan)

# 允许前端页面（如 Nginx 上的站点）跨域接入本 Agent
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # 学习项目全开；生产应收紧为具体站点
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    session_id: str | None = None
    user_id: int | None = None       # 由前端/网关传入（生产应从登录态解析，见 README）
    stream: bool = False             # 预留：流式输出


class ChatResponse(BaseModel):
    session_id: str
    reply: str
    tool_trace: list[dict]


@app.post("/agent/chat", response_model=ChatResponse)
async def chat(req: ChatRequest, authorization: str | None = Header(default=None)):
    session_id = req.session_id or uuid.uuid4().hex
    ctx = ToolContext(user_id=req.user_id, authorization=authorization)
    result = await app.state.loop.run(session_id, req.message.strip(), ctx)
    return ChatResponse(session_id=session_id, reply=result["reply"],
                        tool_trace=result["tool_trace"])


@app.post("/agent/chat/stream")
async def chat_stream(req: ChatRequest, authorization: str | None = Header(default=None)):
    """SSE 流式对话。事件序列：
    start(session_id) → [tool(工具执行完成)...] → delta(回答增量)... → done(reply+trace)
    """
    session_id = req.session_id or uuid.uuid4().hex
    ctx = ToolContext(user_id=req.user_id, authorization=authorization)

    async def gen():
        try:
            yield "data: " + json.dumps({"type": "start", "session_id": session_id},
                                        ensure_ascii=False) + "\n\n"
            async for ev in app.state.loop.run_stream(session_id, req.message.strip(), ctx):
                yield "data: " + json.dumps(ev, ensure_ascii=False) + "\n\n"
        except Exception as e:  # 生成器级兜底：任何未捕获异常以 error 事件收尾
            yield "data: " + json.dumps({"type": "error", "error": str(e)},
                                        ensure_ascii=False) + "\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache",
                                      "X-Accel-Buffering": "no"})


@app.get("/agent/chat/{session_id}/history")
async def chat_history(session_id: str):
    """回放会话历史（页面刷新后重渲染；system 与孤儿消息不在其中）。"""
    return {"session_id": session_id,
            "messages": app.state.sessions.dump(session_id)}


@app.get("/agent/tools")
async def tools():
    return {"tools": app.state.registry.names(), "schemas": app.state.registry.schemas()}


@app.get("/agent/health")
async def health():
    return {"status": "ok"}


@app.get("/chat")
async def chat_page():
    """独立对话页面（同源部署，无跨域问题；也可复制到 Nginx 站点下使用）。"""
    return FileResponse(STATIC_DIR / "chat.html", media_type="text/html")
