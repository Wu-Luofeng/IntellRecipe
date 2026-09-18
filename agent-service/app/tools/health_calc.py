"""健康指标计算工具：BMI / BMR(Mifflin-St Jeor) / TDEE。

算术交给工具而不是 LLM——LLM 心算不可靠，数值类必须代码算。
"""
from __future__ import annotations

from typing import Any, ClassVar

from app.tools.base import Tool
from app.tools.base_ctx import ToolContext

ACTIVITY = {"sedentary": 1.2, "light": 1.375, "moderate": 1.55, "active": 1.725, "athlete": 1.9}


class CalcHealthMetricsTool(Tool):
    name = "calc_health_metrics"
    description = (
        "计算健康指标：BMI 及分级、基础代谢 BMR（Mifflin-St Jeor 公式）、每日总消耗 TDEE。"
        "任何涉及 BMI、基础代谢、每日能吃多少热量的计算都必须用本工具，不要自己心算。"
    )
    parameters: ClassVar[dict] = {
        "type": "object",
        "properties": {
            "height_cm": {"type": "number", "description": "身高（cm）"},
            "weight_kg": {"type": "number", "description": "体重（kg）"},
            "age": {"type": "integer", "description": "年龄（岁），算 BMR 必填"},
            "gender": {"type": "string", "enum": ["male", "female"], "description": "性别"},
            "activity_level": {
                "type": "string",
                "enum": ["sedentary", "light", "moderate", "active", "athlete"],
                "description": "活动水平：久坐/轻度/中度/高强度/运动员，默认 light",
            },
        },
        "required": ["height_cm", "weight_kg"],
    }

    async def execute(self, ctx: ToolContext, **kwargs: Any) -> dict:
        h = float(kwargs["height_cm"])
        w = float(kwargs["weight_kg"])
        if not (80 <= h <= 250) or not (25 <= w <= 400):
            return {"ok": False, "error": "身高/体重数值超出合理范围，请确认后重试"}

        bmi = w / ((h / 100) ** 2)
        result: dict[str, Any] = {
            "ok": True,
            "bmi": round(bmi, 1),
            "bmi_category": self._bmi_category(bmi),
        }

        age = kwargs.get("age")
        gender = kwargs.get("gender")
        if age and gender:
            g = 5 if gender == "male" else -161
            bmr = 10 * w + 6.25 * h - 5 * float(age) + g
            tdee = bmr * ACTIVITY.get(str(kwargs.get("activity_level") or "light"), 1.375)
            result["bmr_kcal"] = round(bmr)
            result["tdee_kcal"] = round(tdee)
            if bmi > 18.5:
                result["mild_weight_loss_target_kcal"] = round(tdee - 300)  # 温和缺口
        return result

    @staticmethod
    def _bmi_category(bmi: float) -> str:
        if bmi < 18.5:
            return "偏瘦"
        if bmi < 24:
            return "正常"
        if bmi < 28:
            return "超重"
        return "肥胖"
