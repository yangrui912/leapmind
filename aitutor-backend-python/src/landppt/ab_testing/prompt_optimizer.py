"""
Prompt optimizer — analyzes A/B experiment results and generates
structured improvement suggestions for the losing prompt version.

Heuristic-driven: maps common validation failure patterns to
prompt-level improvement strategies. Does NOT call an LLM.
"""
import logging
from dataclasses import dataclass, field
from typing import Optional

from .ab_test_framework import ABExperiment, EvaluationReport

logger = logging.getLogger(__name__)


@dataclass
class OptimizationSuggestion:
    """A single concrete suggestion to improve a prompt."""
    target: str                                   # "system_prompt" / "user_prompt"
    action: str                                   # "add" / "remove" / "modify" / "clarify"
    location: str                                 # Where in the prompt (e.g., "before output format section")
    content: str                                  # What to add/change
    expected_impact: str                          # e.g., "提高术语覆盖率", "降低书面化表达"
    priority: str = "medium"                      # "high" / "medium" / "low"


@dataclass
class OptimizationReport:
    """Full optimization analysis result."""
    experiment_name: str
    losing_group: str                             # "A" or "B"
    suggestions: list[OptimizationSuggestion]
    summary: str
    updated_prompt: Optional[str] = None          # Full rewritten prompt (if generated)


# ─── Pattern → Strategy mapping ───

FIX_PATTERNS: dict[str, list[dict]] = {
    "schema_pass_rate": [
        {
            "match": "low",                       # trigger when this metric is below threshold
            "actions": [
                {
                    "target": "system_prompt",
                    "action": "modify",
                    "location": "output format section",
                    "content": "更严格地约束输出格式：在SYSTEM消息末尾重复一次JSON Schema的必填字段列表，并强调'必须包含所有required字段，缺少一个字段整个输出无效'。",
                    "expected_impact": "提高Schema校验通过率",
                    "priority": "high",
                },
            ],
        },
    ],
    "avg_orality_score": [
        {
            "match": "low",
            "actions": [
                {
                    "target": "system_prompt",
                    "action": "add",
                    "location": "before output format section",
                    "content": "【口语化要求】请用口语化教学语言，像真实教师在课堂上讲课。避免使用'本页''如图所示''综上所述''由此可见''该知识点'等书面表达。每段讲稿至少包含2处口语化标记词（如'好''那么''大家注意''同学们'）。",
                    "expected_impact": "提高口语化得分",
                    "priority": "high",
                },
            ],
        },
    ],
    "avg_consistency_score": [
        {
            "match": "low",
            "actions": [
                {
                    "target": "user_prompt",
                    "action": "add",
                    "location": "after the slide content section",
                    "content": "请确保讲稿中覆盖以下所有核心术语：{slide_key_terms}。每个术语至少出现一次。",
                    "expected_impact": "提高术语覆盖率",
                    "priority": "high",
                },
            ],
        },
    ],
    "avg_duration_deviation": [
        {
            "match": "high",                      # deviation should be LOW, high is bad
            "actions": [
                {
                    "target": "system_prompt",
                    "action": "add",
                    "location": "narration length section",
                    "content": "【时长控制】本页目标时长{target_seconds}秒。中文语速约200-250字/分钟，据此控制讲稿长度。{target_seconds}秒对应的讲稿约{recommended_chars}字。请严格控制字数，不要超时也不要过短。",
                    "expected_impact": "减小时长偏差",
                    "priority": "medium",
                },
            ],
        },
    ],
}

# Default thresholds for detecting "poor" metrics
METRIC_THRESHOLDS = {
    "schema_pass_rate": 0.85,
    "avg_orality_score": 0.65,
    "avg_consistency_score": 0.70,
    "avg_duration_deviation": 0.25,
}


class PromptOptimizer:
    """Analyzes A/B results and generates prompt improvement suggestions.

    Usage:
        optimizer = PromptOptimizer()
        report = optimizer.analyze(experiment)
        if report.suggestions:
            print(f"发现 {len(report.suggestions)} 条改进建议")
            # Apply suggestions manually or use apply_suggestions()
            improved_prompt = optimizer.apply_suggestions(
                base_prompt=experiment.prompt_a,
                suggestions=report.suggestions,
            )
    """

    def __init__(self, thresholds: Optional[dict[str, float]] = None):
        self.thresholds = {**METRIC_THRESHOLDS, **(thresholds or {})}

    def analyze(self, experiment: ABExperiment) -> OptimizationReport:
        """Analyze experiment results and generate optimization suggestions.

        Identifies the losing group's weakest metrics and maps them
        to prompt improvement patterns.
        """
        report = experiment.evaluate()
        return self._build_report(experiment, report)

    def analyze_report(self, report: EvaluationReport) -> OptimizationReport:
        """Analyze an already-computed evaluation report."""
        return self._build_report_for_report(report)

    def _build_report(
        self, experiment: ABExperiment, eval_report: EvaluationReport
    ) -> OptimizationReport:
        """Build optimization report from experiment."""
        suggestions: list[OptimizationSuggestion] = []
        losing_group = "A" if eval_report.winner == "B" else "B"

        for metric_name, metric_result in eval_report.metric_results.items():
            metric_threshold = self.thresholds.get(metric_name, 0.0)

            # Determine which group is underperforming
            higher_is_better = metric_result.get("higher_is_better", True)
            b_mean = metric_result.get("b_mean", 0)
            a_mean = metric_result.get("a_mean", 0)

            if higher_is_better:
                losing_value = a_mean if losing_group == "A" else b_mean
            else:
                losing_value = a_mean if losing_group == "A" else b_mean
                # For deviation metrics, lower is better, so compare differently
                if losing_group == "A":
                    is_poor = a_mean > metric_threshold and b_mean < a_mean
                else:
                    is_poor = b_mean > metric_threshold and a_mean < b_mean
                if is_poor and metric_name in FIX_PATTERNS:
                    for pattern in FIX_PATTERNS[metric_name]:
                        for action in pattern["actions"]:
                            suggestions.append(OptimizationSuggestion(**action))
                continue

            # Check if losing group's metric is below threshold
            if losing_value < metric_threshold and metric_name in FIX_PATTERNS:
                for pattern in FIX_PATTERNS[metric_name]:
                    for action in pattern["actions"]:
                        suggestions.append(OptimizationSuggestion(**action))

        summary = (
            f"分析了实验 '{eval_report.experiment_name}' 的 {eval_report.total_samples} 条样本。"
            f"组{losing_group} 在 {len(suggestions)} 个维度需要优化。"
            if suggestions else
            f"分析了实验 '{eval_report.experiment_name}'，未发现需要优化的维度。"
        )

        return OptimizationReport(
            experiment_name=eval_report.experiment_name,
            losing_group=losing_group,
            suggestions=suggestions,
            summary=summary,
        )

    def _build_report_for_report(self, eval_report: EvaluationReport) -> OptimizationReport:
        """Build optimization report from evaluation report only."""
        suggestions: list[OptimizationSuggestion] = []
        losing_group = "A" if eval_report.winner == "B" else "B"

        for metric_name, metric_result in eval_report.metric_results.items():
            metric_threshold = self.thresholds.get(metric_name, 0.0)
            higher_is_better = metric_result.get("higher_is_better", True)
            losing_value = metric_result.get(
                "a_mean" if losing_group == "A" else "b_mean", 0
            )

            metric_ok = (
                losing_value >= metric_threshold
                if higher_is_better
                else losing_value <= metric_threshold
            )

            if not metric_ok and metric_name in FIX_PATTERNS:
                for pattern in FIX_PATTERNS[metric_name]:
                    for action in pattern["actions"]:
                        suggestions.append(OptimizationSuggestion(**action))

        summary = f"基于评估报告，组{losing_group} 有 {len(suggestions)} 条优化建议。"
        return OptimizationReport(
            experiment_name=eval_report.experiment_name,
            losing_group=losing_group,
            suggestions=suggestions,
            summary=summary,
        )

    def apply_suggestions(
        self,
        base_prompt: str,
        suggestions: list[OptimizationSuggestion],
    ) -> str:
        """Apply optimization suggestions to a base prompt string.

        This is a simple text-based approach. For production use,
        consider using an LLM to intelligently merge suggestions.
        """
        prompt = base_prompt
        applied = []

        for suggestion in suggestions:
            # Simple heuristic: append suggestion content before the closing section
            if suggestion.action == "add":
                prompt += f"\n\n{suggestion.content}"
                applied.append(f"added: {suggestion.target} - {suggestion.location}")
            elif suggestion.action == "modify":
                prompt += f"\n\n### 优化说明\n{suggestion.content}"
                applied.append(f"modified: {suggestion.target} - {suggestion.location}")

        if applied:
            logger.info(f"Applied {len(applied)} suggestions to prompt")

        return prompt
