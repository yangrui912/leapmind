"""
JSON Schema definitions for PPT structure and slide validation.

These schemas enforce the structure of Stage 2 AI output:
  - Per-slide structure (type, content, interaction)
  - Full PPT structure (cover page required, min 4 slides)
"""
from typing import Any

# ─── Interaction schema (embedded in slides) ───

INTERACTION_SCHEMA: dict[str, Any] = {
    "type": ["object", "null"],
    "properties": {
        "type": {
            "type": "string",
            "enum": ["choice_question", "think_question", "practice", None],
            "description": "互动类型",
        },
        "question": {"type": "string", "minLength": 3,
                     "description": "问题文本"},
        "options": {
            "type": "array",
            "items": {"type": "string"},
            "description": "选项（选择题时必填）",
        },
        "answer": {"type": "string",
                   "description": "正确答案或参考答案"},
    },
    "required": ["type", "question"],
    "additionalProperties": False,
}

# ─── Single slide schema ───

PPT_SLIDE_SCHEMA: dict[str, Any] = {
    "type": "object",
    "required": ["page_num", "type", "title", "bullet_points"],
    "properties": {
        "page_num": {"type": "integer", "minimum": 1,
                     "description": "页码"},
        "type": {
            "type": "string",
            "enum": ["cover", "content", "interactive", "summary", "homework"],
            "description": "幻灯片类型",
        },
        "title": {"type": "string", "minLength": 1,
                  "description": "幻灯片标题"},
        "bullet_points": {
            "type": "array",
            "minItems": 1,
            "items": {"type": "string", "minLength": 2},
            "description": "正文要点列表",
        },
        "image_suggestion": {
            "type": "string",
            "description": "配图描述（可为空字符串）",
        },
        "formula": {
            "type": "string",
            "description": "LaTeX公式（可为空字符串）",
        },
        "highlight_points": {
            "type": "array",
            "items": {"type": "string"},
            "description": "需高亮的关键词",
        },
        "interaction": INTERACTION_SCHEMA,
        "is_fallback": {
            "type": "boolean",
            "description": "是否为生成失败的占位页",
        },
    },
    "additionalProperties": False,
}

# ─── Full PPT structure (collection of slides) ───

PPT_STRUCTURE_SCHEMA: dict[str, Any] = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "required": ["title", "total_pages", "slides"],
    "properties": {
        "title": {"type": "string", "minLength": 1,
                  "description": "PPT标题"},
        "total_pages": {"type": "integer", "minimum": 1,
                        "description": "总页数"},
        "estimated_duration": {
            "type": "integer", "minimum": 1,
            "description": "预计讲解时长（分钟）",
        },
        "slides": {
            "type": "array",
            "minItems": 1,
            "items": PPT_SLIDE_SCHEMA,
            "description": "幻灯片列表",
        },
    },
    "additionalProperties": False,
}
