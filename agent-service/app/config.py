"""应用配置：config.yaml + 环境变量覆盖（api_key 优先取 LLM_API_KEY）。"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

import yaml

BASE_DIR = Path(__file__).resolve().parent.parent


@dataclass
class LLMConfig:
    base_url: str = "https://api.deepseek.com/v1"
    model: str = "deepseek-chat"
    api_key: str = ""
    temperature: float = 0.6
    max_tokens: int = 2048


@dataclass
class DBConfig:
    host: str = "127.0.0.1"
    port: int = 3307
    user: str = "root"
    password: str = "root"
    database: str = "intell_recipe"
    pool_min: int = 1
    pool_max: int = 5


@dataclass
class APIConfig:
    gateway_base: str = "http://localhost:10010"
    timeout_seconds: int = 10
    max_response_chars: int = 4000
    # 数据来源接口契约（Java 侧按此实现；路径可在 yaml 调整）
    #   user_profile     已存在：GET /user/me（authorization 头鉴权）
    #   today_diet       已存在：GET /diet/today
    #   user_preference  计划新增：GET 用户饮食偏好
    #   recipes_search   计划新增：GET 菜品条件查询（keyword/cuisine/taste/maxCalories/minProtein/limit）
    endpoints: dict = field(default_factory=lambda: {
        "user_profile": "/user/me",
        "today_diet": "/diet/today",
        "user_preference": "/recipe/agent/preference",
        "recipes_search": "/recipe/agent/recipes",
    })


@dataclass
class AgentConfig:
    max_iterations: int = 6
    history_max_messages: int = 40


@dataclass
class AppConfig:
    llm: LLMConfig = field(default_factory=LLMConfig)
    db: DBConfig = field(default_factory=DBConfig)
    api: APIConfig = field(default_factory=APIConfig)
    agent: AgentConfig = field(default_factory=AgentConfig)


def _load_dotenv(path: Path) -> None:
    """极简 .env 加载（KEY=VALUE，# 注释），不引入额外依赖。已存在的环境变量优先。"""
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


def load_config(path: str | None = None) -> AppConfig:
    """读取 config.yaml + .env；api_key 环境变量优先（避免密钥入库）。"""
    _load_dotenv(BASE_DIR / ".env")
    cfg_path = Path(path) if path else BASE_DIR / "config.yaml"
    raw: dict = {}
    if cfg_path.exists():
        with open(cfg_path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f) or {}

    cfg = AppConfig(
        llm=LLMConfig(**(raw.get("llm") or {})),
        db=DBConfig(**(raw.get("db") or {})),
        api=APIConfig(**(raw.get("api") or {})),
        agent=AgentConfig(**(raw.get("agent") or {})),
    )
    env_key = os.getenv("LLM_API_KEY", "").strip()
    if env_key:
        cfg.llm.api_key = env_key
    return cfg
