"""
Narration text quality validator.

Three-dimensional validation:
  1. Orality       — 口语化程度（标记词密度/书面词/句长）
  2. Duration      — 时长合理性（单页/总时长偏差）
  3. Consistency   — 与幻灯片内容一致性（术语覆盖率）
"""
import re
import math
import logging
from dataclasses import dataclass, field
from typing import Optional

logger = logging.getLogger(__name__)


# ─── Score models ───

@dataclass
class OralityScore:
    score: float                         # 0~1
    marker_density: float                # 口语标记词/百字
    formal_patterns_found: list[str]     # 发现的书面化表达
    avg_sentence_length: float           # 句均字数
    deductions: list[str] = field(default_factory=list)


@dataclass
class DurationScore:
    is_reasonable: bool
    estimated_seconds: float
    target_seconds: int
    deviation: float                     # |estimated - target| / target
    violations: list[str] = field(default_factory=list)


@dataclass
class ConsistencyScore:
    coverage: float                      # 0~1
    total_terms: int
    covered_terms: int
    missing_terms: list[str] = field(default_factory=list)


@dataclass
class ValidationReport:
    """Combined validation report for a single narration."""
    passed: bool
    orality: OralityScore
    duration: DurationScore
    consistency: ConsistencyScore
    overall_score: float                 # weighted average

    def to_dict(self) -> dict:
        return {
            "passed": self.passed,
            "overall_score": round(self.overall_score, 3),
            "orality": {
                "score": round(self.orality.score, 3),
                "marker_density": round(self.orality.marker_density, 2),
                "formal_patterns": self.orality.formal_patterns_found,
                "avg_sentence_length": round(self.orality.avg_sentence_length, 1),
                "deductions": self.orality.deductions,
            },
            "duration": {
                "is_reasonable": self.duration.is_reasonable,
                "estimated_seconds": int(self.duration.estimated_seconds),
                "target_seconds": self.duration.target_seconds,
                "deviation": round(self.duration.deviation, 3),
                "violations": self.duration.violations,
            },
            "consistency": {
                "coverage": round(self.consistency.coverage, 3),
                "covered": self.consistency.covered_terms,
                "total": self.consistency.total_terms,
                "missing": self.consistency.missing_terms,
            },
        }


@dataclass
class ValidatorThresholds:
    """Configurable thresholds for validation."""
    min_orality_score: float = 0.6
    min_marker_density: float = 1.5      # 口语标记词/百字
    max_avg_sentence_length: float = 30   # 句均最大字数
    max_duration_deviation: float = 0.25  # 25%
    min_slide_duration: float = 15        # 秒
    max_slide_duration: float = 180       # 秒
    min_consistency_coverage: float = 0.7
    speaking_speed_slow: int = 180        # 字/分钟
    speaking_speed_normal: int = 240
    speaking_speed_fast: int = 300


# ─── Narration Validator ───

class NarrationValidator:
    """Three-dimensional narration quality validator."""

    # 口语化标记词库
    ORAL_MARKERS = [
        "好", "那么", "大家", "注意", "明白了", "其实",
        "也就是说", "换句话说", "比如", "同学们", "咱们",
        "你看", "想想", "是不是", "对不对", "没错",
        "对", "这个", "那个", "然后", "所以呢",
        "当然", "我们", "来看", "知道", "意思",
    ]

    # 书面化禁用模式（正则）
    FORMAL_PATTERNS = [
        (r"本页", '"本页" — 口语中应说"这一页"或"这里"'),
        (r"如图所示", '"如图所示" — 口语中应说"大家看这张图"'),
        (r"综上所述", '"综上所述" — 口语中应说"总的来说"'),
        (r"由此可见", '"由此可见" — 口语中应说"所以"'),
        (r"以下", '"以下" — 口语中应说"下面"'),
        (r"上述", '"上述" — 口语中应说"刚才说的"'),
        (r"该知识点", '"该知识点" — 口语中应说"这个知识点"'),
        (r"本节[课章]", '"本节" — 口语中应说"这一节"'),
        (r"亦即", '"亦即" — 口语中应说"也就是"'),
        (r"故而", '"故而" — 口语中应说"所以"'),
        (r"此外", '"此外" — 口语中应说"另外"'),
        (r"及其", '"及其" — 口语中应说"和"'),
        (r"均", '"均" — 口语中应说"都"'),
        (r"若干", '"若干" — 口语中应说"一些"'),
    ]

    # 标点符号（用于分句）
    SENTENCE_DELIMITERS = re.compile(r"[。！？；\n]")

    def __init__(self, thresholds: Optional[ValidatorThresholds] = None):
        self.thresholds = thresholds or ValidatorThresholds()

    # ─── Public API ───

    def validate(
        self,
        narration_text: str,
        slide: Optional[dict] = None,
        target_seconds: Optional[int] = None,
        speed: str = "normal",
    ) -> ValidationReport:
        """Run all three checks and produce a combined report."""
        orality = self.check_orality(narration_text)
        duration = self.check_duration(narration_text, target_seconds, speed)
        consistency = self.check_consistency(narration_text, slide or {})

        # Weighted overall score
        overall = (
            orality.score * 0.35
            + (1.0 if duration.is_reasonable else max(0, 1.0 - duration.deviation)) * 0.30
            + consistency.coverage * 0.35
        )

        passed = (
            orality.score >= self.thresholds.min_orality_score
            and duration.is_reasonable
            and consistency.coverage >= self.thresholds.min_consistency_coverage
        )

        return ValidationReport(
            passed=passed,
            orality=orality,
            duration=duration,
            consistency=consistency,
            overall_score=overall,
        )

    # ─── 1. Orality Check ───

    def check_orality(self, text: str) -> OralityScore:
        """Evaluate how colloquial/oral the narration text is."""
        if not text or not text.strip():
            return OralityScore(
                score=0.0, marker_density=0.0,
                formal_patterns_found=[], avg_sentence_length=0,
                deductions=["空文本"],
            )

        deductions: list[str] = []
        total_chars = len(text.replace(" ", "").replace("\n", ""))

        # Marker density
        marker_count = sum(text.count(m) for m in self.ORAL_MARKERS)
        # Avoid double-counting overlapping markers
        density = (marker_count / max(total_chars, 1)) * 100  # per 100 chars

        if density < self.thresholds.min_marker_density:
            deductions.append(
                f"口语标记词密度偏低 ({density:.1f}/百字, "
                f"建议 ≥{self.thresholds.min_marker_density})"
            )

        # Formal pattern detection
        formal_found: list[str] = []
        for pattern, suggestion in self.FORMAL_PATTERNS:
            if re.search(pattern, text):
                formal_found.append(suggestion)

        if formal_found:
            deductions.append(f"存在 {len(formal_found)} 处书面化表达")

        # Average sentence length
        sentences = [
            s.strip() for s in self.SENTENCE_DELIMITERS.split(text)
            if s.strip()
        ]
        if sentences:
            avg_len = sum(len(s) for s in sentences) / len(sentences)
        else:
            avg_len = total_chars

        if avg_len > self.thresholds.max_avg_sentence_length:
            deductions.append(
                f"句子偏长 (均字 {avg_len:.0f}, "
                f"建议 ≤{self.thresholds.max_avg_sentence_length})"
            )

        # Compute score
        score = 1.0
        if density < self.thresholds.min_marker_density:
            score -= 0.15
        if formal_found:
            score -= min(0.30, len(formal_found) * 0.05)
        if avg_len > self.thresholds.max_avg_sentence_length:
            score -= 0.15
        if total_chars < 20:
            score -= 0.5  # Very short text is probably not useful narration

        score = max(0.0, min(1.0, score))

        return OralityScore(
            score=score,
            marker_density=density,
            formal_patterns_found=formal_found,
            avg_sentence_length=avg_len,
            deductions=deductions,
        )

    # ─── 2. Duration Check ───

    def check_duration(
        self,
        text: str,
        target_seconds: Optional[int] = None,
        speed: str = "normal",
    ) -> DurationScore:
        """Evaluate duration reasonableness.

        Estimate based on Chinese speaking rate (chars per minute).
        Includes pause estimation.
        """
        if not text or not text.strip():
            return DurationScore(
                is_reasonable=False,
                estimated_seconds=0,
                target_seconds=target_seconds or 60,
                deviation=1.0,
                violations=["空文本"],
            )

        # Estimate speaking duration
        char_count = len(text.replace(" ", "").replace("\n", ""))
        speed_map = {
            "slow": self.thresholds.speaking_speed_slow,
            "normal": self.thresholds.speaking_speed_normal,
            "fast": self.thresholds.speaking_speed_fast,
        }
        cpm = speed_map.get(speed, self.thresholds.speaking_speed_normal)
        base_seconds = (char_count / cpm) * 60

        # Estimate pause time (rough: 0.5s per sentence break + 1s per question mark)
        sentence_count = len(self.SENTENCE_DELIMITERS.split(text)) - 1
        question_count = text.count("？") + text.count("?")
        pause_seconds = sentence_count * 0.3 + question_count * 0.8

        estimated = base_seconds + pause_seconds

        violations: list[str] = []

        # Absolute bounds check
        if estimated < self.thresholds.min_slide_duration:
            violations.append(
                f"时长过短 ({estimated:.0f}s < {self.thresholds.min_slide_duration}s)"
            )
        if estimated > self.thresholds.max_slide_duration:
            violations.append(
                f"时长过长 ({estimated:.0f}s > {self.thresholds.max_slide_duration}s)"
            )

        # Target deviation check
        if target_seconds is not None and target_seconds > 0:
            deviation = abs(estimated - target_seconds) / target_seconds
            is_reasonable = deviation <= self.thresholds.max_duration_deviation
            if not is_reasonable:
                violations.append(
                    f"与目标时长偏差 {deviation:.1%} "
                    f"(目标 {target_seconds}s, 预估 {estimated:.0f}s)"
                )
        else:
            deviation = 0.0
            is_reasonable = True

        return DurationScore(
            is_reasonable=is_reasonable,
            estimated_seconds=estimated,
            target_seconds=target_seconds or 60,
            deviation=deviation,
            violations=violations,
        )

    # ─── 3. Consistency Check ───

    def check_consistency(self, narration_text: str, slide: dict) -> ConsistencyScore:
        """Check that key terms from the slide appear in narration text."""
        # Extract key terms from slide
        terms: set[str] = set()

        # Title
        title = slide.get("title", "")
        if title:
            terms.update(self._extract_key_terms(title))

        # Bullet points
        for point in slide.get("bullet_points", []):
            if isinstance(point, str):
                terms.update(self._extract_key_terms(point))

        # Highlight points
        for hp in slide.get("highlight_points", []):
            if isinstance(hp, str):
                terms.add(hp.strip())

        # Formula reference
        formula = slide.get("formula", "")
        if formula and formula not in ("", "无"):
            # Extract formula variable names
            var_terms = re.findall(r'[a-zA-Zα-ωΑ-Ω]+', formula)
            terms.update(v for v in var_terms if len(v) > 1)

        # Remove very short terms (1 char) and common words
        terms = {
            t for t in terms
            if len(t) >= 2 and t.lower() not in {
                "the", "is", "in", "of", "to", "and", "a", "an",
                "for", "on", "by", "at", "be", "or", "as",
            }
        }

        if not terms:
            return ConsistencyScore(
                coverage=1.0, total_terms=0,
                covered_terms=0, missing_terms=[],
            )

        # Check term coverage
        text_lower = narration_text.lower()
        missing: list[str] = []
        covered_count = 0

        for term in sorted(terms):
            if term.lower() in text_lower:
                covered_count += 1
            else:
                missing.append(term)

        coverage = covered_count / len(terms)

        return ConsistencyScore(
            coverage=coverage,
            total_terms=len(terms),
            covered_terms=covered_count,
            missing_terms=missing,
        )

    # ─── Helpers ───

    @staticmethod
    def _extract_key_terms(text: str) -> set[str]:
        """Extract meaningful key terms from text fragment."""
        terms: set[str] = set()

        # Remove punctuation
        # Remove punctuation via translation table (no regex escaping issues)
        punct_chars = "，。！？、；：「」""''（）【】《》,.?!;:()[]{} -"
        trans_table = str.maketrans({c: " " for c in punct_chars})
        clean = text.translate(trans_table)

        # Split into segments
        for segment in clean.split():
            segment = segment.strip()
            if not segment or len(segment) < 2:
                continue

            # Chinese terms (2-10 chars)
            if re.match(r"^[一-鿿]{2,10}$", segment):
                terms.add(segment)

            # Mixed Chinese-En/num terms like "勾股定理a²"
            elif re.match(r"^[一-鿿\w]{2,20}$", segment):
                terms.add(segment)

        return terms

    # ─── 4. Correct narration data based on validation ───

    def correct_narration(
        self,
        narration: dict,
        report: ValidationReport,
        speed: str = "normal",
    ) -> dict:
        """Apply validation results back to narration data.

        Corrections applied:
          - Override estimated_duration_seconds with validator's calculation
          - Add _validation metadata block for frontend use
          - Flag oral issues for human review

        Args:
            narration: Raw narration dict from AI
            report: ValidationReport from validate()
            speed: Speaking speed used for duration calculation

        Returns:
            Corrected narration dict with _validation metadata.
        """
        corrected = dict(narration)

        # 1. Override duration with validator's estimate (more reliable)
        corrected["estimated_duration_seconds"] = int(report.duration.estimated_seconds)

        # 2. Flag duration mismatch if significant
        dur = report.duration
        if dur.target_seconds and dur.target_seconds > 0:
            corrected["duration_target_seconds"] = dur.target_seconds
            corrected["duration_deviation"] = round(dur.deviation, 3)

        # 3. Add validation metadata block for frontend
        corrected["_validation"] = report.to_dict()

        # 4. If orality is poor, add suggestion flags
        if report.orality.score < self.thresholds.min_orality_score:
            corrected["_needs_review"] = True
            corrected["_review_reason"] = "口语化不足"

        return corrected
