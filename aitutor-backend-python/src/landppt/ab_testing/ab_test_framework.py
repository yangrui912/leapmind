"""
A/B test framework for Prompt version comparison.

Features:
  - Consistent hash-based user assignment (stable across requests)
  - Multi-metric tracking (schema_pass_rate, orality, duration, consistency)
  - Statistical evaluation with Cohen's d effect size
  - Auto-decision: declare winner when lift > 5% and effect size > 0.2
"""
import math
import hashlib
import statistics
import logging
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Optional

logger = logging.getLogger(__name__)


# ─── Metric definition ───

@dataclass
class MetricDefinition:
    """Definition of a single evaluation metric."""
    name: str                                          # e.g. "schema_pass_rate"
    description: str                                   # e.g. "JSON Schema校验通过率"
    higher_is_better: bool = True                      # True = higher score is better
    weight: float = 1.0                                # Weight in composite score


# ─── Sample record ───

@dataclass
class SampleRecord:
    """One data point from an A/B test."""
    user_id: int
    group: str                                         # "A" or "B"
    prompt_version: str                                # e.g. "v1.0" or "v2.0"
    metrics: dict[str, float]                          # metric_name → value
    timestamp: float = 0.0
    metadata: dict = field(default_factory=dict)       # Extra info

    def __post_init__(self):
        if not self.timestamp:
            self.timestamp = datetime.now().timestamp()


# ─── Evaluation report ───

@dataclass
class EvaluationReport:
    """Statistical comparison between group A and group B."""
    experiment_name: str
    total_samples: int
    group_a_count: int
    group_b_count: int
    metric_results: dict[str, dict]                    # metric_name → {a_mean, b_mean, lift, cohens_d}
    winner: str                                        # "A", "B", or "tie"
    composite_lift: float                              # Weighted average lift across metrics
    recommendation: str                                # Human-readable suggestion

    def to_dict(self) -> dict:
        return {
            "experiment_name": self.experiment_name,
            "total_samples": self.total_samples,
            "group_a_count": self.group_a_count,
            "group_b_count": self.group_b_count,
            "winner": self.winner,
            "composite_lift": round(self.composite_lift, 4),
            "recommendation": self.recommendation,
            "metric_results": {
                k: {
                    "a_mean": round(v["a_mean"], 4),
                    "b_mean": round(v["b_mean"], 4),
                    "lift": round(v["lift"], 4),
                    "cohens_d": round(v["cohens_d"], 4),
                    "higher_is_better": v["higher_is_better"],
                }
                for k, v in self.metric_results.items()
            },
        }


# ─── Default metrics for lesson prep ───

DEFAULT_METRICS = [
    MetricDefinition("schema_pass_rate", "JSON Schema校验通过率", higher_is_better=True, weight=0.25),
    MetricDefinition("avg_orality_score", "口语化得分均值", higher_is_better=True, weight=0.25),
    MetricDefinition("avg_consistency_score", "术语覆盖率均值", higher_is_better=True, weight=0.20),
    MetricDefinition("avg_duration_deviation", "时长偏差均值", higher_is_better=False, weight=0.15),
    MetricDefinition("avg_generation_time", "AI生成耗时均值(秒)", higher_is_better=False, weight=0.15),
]


# ─── Main experiment class ───

class ABExperiment:
    """A/B test experiment for comparing two Prompt versions.

    Usage:
        exp = ABExperiment(
            name="syllabus_v1_vs_v2",
            prompt_a="original prompt...",
            prompt_b="new prompt...",
        )
        group = exp.assign(user_id=1001)
        prompt = exp.prompt_a if group == "A" else exp.prompt_b
        # ... generate content ...
        exp.record_result(user_id=1001, group=group, metrics={
            "schema_pass_rate": 0.95,
            "avg_orality_score": 0.82,
        })
        report = exp.evaluate()
    """

    def __init__(
        self,
        name: str,
        prompt_a: str,
        prompt_b: str,
        metrics: Optional[list[MetricDefinition]] = None,
        traffic_split: float = 0.5,
        target_sample_size: int = 100,
        min_sample_size: int = 20,
    ):
        if not 0 < traffic_split < 1:
            raise ValueError("traffic_split must be between 0 and 1")
        self.name = name
        self.prompt_a = prompt_a
        self.prompt_b = prompt_b
        self.metrics = metrics or DEFAULT_METRICS
        self.traffic_split = traffic_split
        self.target_sample_size = target_sample_size
        self.min_sample_size = min_sample_size
        self.status: str = "running"          # running / evaluating / concluded
        self.samples: list[SampleRecord] = []

    # ─── User assignment ───

    def assign(self, user_id: int) -> str:
        """Assign user to group A or B using consistent hashing.

        The same user_id always gets the same group for the same experiment,
        ensuring stable assignment across requests.
        """
        if self.status != "running":
            return "A"  # Default to A when experiment is not active

        hash_input = f"{self.name}:{user_id}"
        hash_hex = hashlib.md5(hash_input.encode()).hexdigest()
        hash_int = int(hash_hex[:8], 16) % 100

        group = "A" if hash_int < self.traffic_split * 100 else "B"
        return group

    # ─── Record keeping ───

    def record_result(
        self,
        user_id: int,
        group: str,
        metrics: dict[str, float],
        metadata: Optional[dict] = None,
    ) -> None:
        """Record one A/B test sample."""
        prompt_version = "v1.0" if group == "A" else "v2.0"
        record = SampleRecord(
            user_id=user_id,
            group=group,
            prompt_version=prompt_version,
            metrics=dict(metrics),
            metadata=metadata or {},
        )
        self.samples.append(record)
        logger.info(
            f"ABTest [{self.name}] user={user_id} group={group} "
            f"metrics={metrics}"
        )

    def get_group_samples(self, group: str) -> list[SampleRecord]:
        """Get all samples for a specific group."""
        return [s for s in self.samples if s.group == group]

    # ─── Statistical evaluation ───

    def evaluate(self) -> EvaluationReport:
        """Evaluate A/B test results and declare a winner.

        Uses Cohen's d for effect size. Declares winner when:
          1. Minimum sample size reached in both groups
          2. Composite lift > 5%
          3. Average effect size > 0.2
        """
        group_a = self.get_group_samples("A")
        group_b = self.get_group_samples("B")

        total = len(self.samples)
        a_count = len(group_a)
        b_count = len(group_b)

        metric_results: dict[str, dict] = {}
        composite_lift = 0.0
        total_weight = 0.0
        effect_sizes: list[float] = []

        for metric in self.metrics:
            a_values = [
                s.metrics.get(metric.name, 0) for s in group_a
                if metric.name in s.metrics
            ]
            b_values = [
                s.metrics.get(metric.name, 0) for s in group_b
                if metric.name in s.metrics
            ]

            if not a_values or not b_values:
                metric_results[metric.name] = {
                    "a_mean": 0, "b_mean": 0, "lift": 0, "cohens_d": 0,
                    "higher_is_better": metric.higher_is_better,
                    "error": "insufficient data",
                }
                continue

            a_mean = statistics.mean(a_values)
            b_mean = statistics.mean(b_values)

            # Lift calculation
            if a_mean != 0:
                raw_lift = (b_mean - a_mean) / abs(a_mean)
            else:
                raw_lift = 0 if b_mean == 0 else 1.0

            # For metrics where lower is better, negate the lift
            lift = raw_lift if metric.higher_is_better else -raw_lift

            # Cohen's d (effect size)
            a_std = statistics.stdev(a_values) if len(a_values) > 1 else 0
            b_std = statistics.stdev(b_values) if len(b_values) > 1 else 0
            pooled_std = math.sqrt(
                ((len(a_values) - 1) * a_std**2 + (len(b_values) - 1) * b_std**2)
                / max(len(a_values) + len(b_values) - 2, 1)
            )
            cohens_d = (b_mean - a_mean) / pooled_std if pooled_std > 0 else 0
            # Normalize: positive d always means B is better
            if not metric.higher_is_better:
                cohens_d = -cohens_d

            metric_results[metric.name] = {
                "a_mean": a_mean,
                "b_mean": b_mean,
                "lift": lift,
                "cohens_d": cohens_d,
                "higher_is_better": metric.higher_is_better,
            }

            # Weighted composite
            composite_lift += lift * metric.weight
            total_weight += metric.weight
            effect_sizes.append(abs(cohens_d))

        composite_lift = composite_lift / max(total_weight, 0.001)
        avg_effect = statistics.mean(effect_sizes) if effect_sizes else 0

        # Decision logic
        enough_data = a_count >= self.min_sample_size and b_count >= self.min_sample_size
        significant_lift = abs(composite_lift) > 0.05
        meaningful_effect = avg_effect > 0.2

        if enough_data and significant_lift and meaningful_effect:
            winner = "B" if composite_lift > 0 else "A"
            recommendation = (
                f"组{winner} 在复合指标上领先 {abs(composite_lift):.1%} "
                f"(平均效应量 d={avg_effect:.2f})，建议采用。"
            )
        elif enough_data:
            winner = "tie"
            recommendation = (
                f"两组差异不显著 (lift={composite_lift:.1%}, d={avg_effect:.2f})，"
                f"建议保持A组（当前版本），继续收集数据。"
            )
        else:
            winner = "pending"
            recommendation = (
                f"数据不足 (A={a_count}, B={b_count})，"
                f"目标每组至少 {self.min_sample_size} 条。继续收集。"
            )

        return EvaluationReport(
            experiment_name=self.name,
            total_samples=total,
            group_a_count=a_count,
            group_b_count=b_count,
            metric_results=metric_results,
            winner=winner,
            composite_lift=composite_lift,
            recommendation=recommendation,
        )

    # ─── Status management ───

    def conclude(self) -> EvaluationReport:
        """Finalize experiment and return final evaluation."""
        self.status = "concluded"
        return self.evaluate()

    def summary(self) -> str:
        """Quick status summary string."""
        report = self.evaluate()
        lines = [
            f"AB实验 [{self.name}]",
            f"  状态: {self.status}",
            f"  样本: A={report.group_a_count}, B={report.group_b_count}",
            f"  胜者: {report.winner}",
            f"  综合提升: {report.composite_lift:.1%}",
        ]
        lines.append(f"  建议: {report.recommendation}")
        return "\n".join(lines)
