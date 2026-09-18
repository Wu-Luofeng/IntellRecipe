"""冒烟测试：真实调用 LLM，验证 Provider / AgentLoop / 工具循环。

用法：
  cd agent-service
  python scripts/smoke.py

用例：
  1) Provider 直连：纯对话
  2) AgentLoop + calc_health_metrics：验证工具调用循环与最终回答
  3) 未登录调用户工具：验证身份缺失时的优雅失败
  4) Java 接口数据链路：recommend_dishes（网关/Java 接口已实现并启动后可用）
"""
from __future__ import annotations

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import load_config
from app.core.loop import AgentLoop
from app.core.provider import OpenAICompatProvider
from app.tools.base_ctx import ToolContext
from app.tools.factory import build_registry


async def main() -> None:
    cfg = load_config()
    if not cfg.llm.api_key:
        print("!! 缺少 LLM_API_KEY（.env），退出")
        return
    print(f"== provider: {cfg.llm.base_url} / {cfg.llm.model}")
    print(f"== 数据源网关: {cfg.api.gateway_base}（接口契约见 config.yaml 的 api.endpoints）")

    provider = OpenAICompatProvider(cfg.llm.base_url, cfg.llm.api_key, cfg.llm.model)
    registry = build_registry(cfg.api)
    loop = AgentLoop(provider, registry, SessionStoreForSmoke(), cfg.agent.max_iterations)

    # 用例 1：直连对话
    msg = await provider.chat([{"role": "user", "content": "用一句话回答：成年人每天建议饮水多少毫升？"}])
    print("\n[1] 直连对话 ->", (msg.get("content") or "").strip()[:120])

    # 用例 2：工具循环（纯计算，不依赖 Java 接口）
    r = await loop.run("smoke-2", "帮我算算：身高175cm、体重70kg、28岁男性，BMI和每日大概能吃多少千卡？",
                       ToolContext(user_id=1))
    print("\n[2] 工具循环 reply ->", r["reply"][:260])
    print("    tool_trace ->", [(t["tool"], t["result"][:80]) for t in r["tool_trace"]])

    # 用例 3：未登录调用户工具的优雅失败
    r = await loop.run("smoke-3", "根据我的身体数据，我早餐适合吃什么？", ToolContext(user_id=None))
    print("\n[3] 未登录场景 reply ->", r["reply"][:220])
    print("    tool_trace ->", [(t["tool"], t["result"][:80]) for t in r["tool_trace"]])

    # 用例 4：Java 接口数据链路（需网关与对应 Java 接口已就绪）
    r = await loop.run("smoke-4", "我今晚吃点什么好？帮我推荐几个菜。", ToolContext(user_id=1))
    print("\n[4] 菜品推荐 reply ->", r["reply"][:400])
    print("    tool_trace ->", [(t["tool"], t["result"][:120]) for t in r["tool_trace"]])

    await provider.close()
    print("\n== smoke done ==")


def SessionStoreForSmoke():
    from app.core.session import SessionStore
    return SessionStore(40)


if __name__ == "__main__":
    asyncio.run(main())
