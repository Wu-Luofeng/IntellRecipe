"""工具上下文：承载 LLM 不可见、不可伪造的可信信息（当前用户身份、透传凭证）。

设计要点：user_id 由调用方（前端/网关）传入请求体，随 ToolContext 注入工具执行，
而不是让 LLM 自己编参数——防止横向越权（LLM 编个 user_id 查别人的数据）。
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ToolContext:
    user_id: int | None = None
    authorization: str | None = None   # 前端登录 token，透传给需要鉴权的业务接口
