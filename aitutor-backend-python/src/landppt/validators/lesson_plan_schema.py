"""
JSON Schema definitions for lesson plan (syllabus) validation.

These schemas enforce the structure of Stage 1 AI output:
  - Top-level syllabus structure
  - Each section's content requirements
  - Teaching process (5 required stages)
  - Homework structure
"""
from typing import Any

# ─── Helper: reusable teaching_process step schema ───

TEACHING_PROCESS_STEP_SCHEMA: dict[str, Any] = {
    "type": "object",
    "required": ["step", "duration", "teacher_activity", "student_activity", "design_intent"],
    "properties": {
        "step": {"type": "string", "description": "环节名称（如课堂导入、新知讲授等）"},
        "duration": {"type": "string", "pattern": r"^\d+.*(min|分钟)?$",
                     "description": "时长，如 5min"},
        "teacher_activity": {"type": "string", "minLength": 2,
                             "description": "教师活动描述"},
        "student_activity": {"type": "string", "minLength": 2,
                             "description": "学生活动描述"},
        "design_intent": {"type": "string", "minLength": 2,
                          "description": "设计意图"},
    },
    "additionalProperties": False,
}

# ─── Homework schema ───

HOMEWORK_SCHEMA: dict[str, Any] = {
    "type": "object",
    "properties": {
        "basic": {
            "type": "array",
            "items": {"type": "string"},
            "description": "基础题",
        },
        "advanced": {
            "type": "array",
            "items": {"type": "string"},
            "description": "提高题",
        },
        "optional": {
            "type": "array",
            "items": {"type": "string"},
            "description": "拓展题（可选）",
        },
    },
    "additionalProperties": False,
}

# ─── Per-section detail schema ───

LESSON_DETAIL_SCHEMA: dict[str, Any] = {
    "type": "object",
    "required": ["hour_index", "title", "core_content",
                  "teaching_goals", "key_points",
                  "difficult_points", "teaching_process"],
    "properties": {
        "hour_index": {"type": "integer", "minimum": 1,
                       "description": "课时序号"},
        "title": {"type": "string", "minLength": 2,
                  "description": "本课时标题"},
        "core_content": {"type": "string", "minLength": 5,
                         "description": "核心内容概述"},
        "teaching_goals": {
            "type": "array", "minItems": 1,
            "items": {"type": "string", "minLength": 4},
            "description": "教学目标列表",
        },
        "key_points": {
            "type": "array", "minItems": 1,
            "items": {"type": "string", "minLength": 2},
            "description": "教学重点",
        },
        "difficult_points": {
            "type": "array", "minItems": 1,
            "items": {"type": "string", "minLength": 2},
            "description": "教学难点",
        },
        "teaching_process": {
            "type": "array",
            "minItems": 3,
            "items": TEACHING_PROCESS_STEP_SCHEMA,
            "description": "教学过程（至少3个环节，推荐5个：导入、讲授、互动、练习、小结）",
        },
        "homework": {
            "type": "object",
            "properties": {
                "basic": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "advanced": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "optional": {
                    "type": "array",
                    "items": {"type": "string"},
                },
            },
            "additionalProperties": False,
        },
    },
    "additionalProperties": False,
}

# ─── Top-level lesson plan (syllabus) schema ───

LESSON_PLAN_SCHEMA: dict[str, Any] = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["title", "total_hours", "sections"],
    "properties": {
        "title": {"type": "string", "minLength": 1, "maxLength": 200},
        "total_hours": {"type": "integer", "minimum": 1, "maximum": 10},
        "knowledge_structure": {
            "type": "string",
            "description": "知识点之间逻辑关系说明",
        },
        "sections": {
            "type": "array",
            "minItems": 1,
            "items": LESSON_DETAIL_SCHEMA,
        },
    },
    "additionalProperties": False,
}

# ─── Outline-level schema (parsed from streaming output before sections are expanded) ───

OUTLINE_SCHEMA: dict[str, Any] = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["title", "total_hours", "sections"],
    "properties": {
        "title": {"type": "string"},
        "total_hours": {"type": "integer", "minimum": 1},
        "sections": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["hour_index", "title", "core_content"],
                "properties": {
                    "hour_index": {"type": "integer"},
                    "title": {"type": "string"},
                    "core_content": {"type": "string"},
                },
            },
        },
    },
}
