"""
内部 API 端点 —— Java Spring Boot 通过 HTTP 调用 Python AI 服务

Java → Python 内部通信规范参考：
  POST /api/internal/ai/generate              — 非流式 AI 调用
  POST /api/internal/ai/generate/stream       — 流式 AI 调用 (SSE)
  POST /api/internal/ai/generate-lesson-prep  — M5 备课生成（完整三段管线）
"""
import logging
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from ..ai.providers import set_ai_override, reset_ai_override
from ..services.lesson_prep_service import LessonPrepService, PrepContext

router = APIRouter(prefix="/api/internal", tags=["Internal AI API (Java→Python)"])
logger = logging.getLogger(__name__)


def _override_to_provider_config(override) -> Optional[dict]:
    """把前端提交的 aiOverride 转成 provider 配置键；无有效配置时返回 None。"""
    if not override or not isinstance(override, dict):
        return None
    config = {}
    base_url = override.get("baseUrl") or override.get("base_url")
    credential = override.get("apiKey") or override.get("api_key")
    model = override.get("model")
    if base_url:
        config["base_url"] = base_url
    if credential:
        config["api_key"] = credential
    if model:
        config["model"] = model
    return config or None


async def _stream_with_override(generator, provider_config):
    """在流式响应期间保持请求级 AI 配置，结束后恢复。"""
    token = set_ai_override(provider_config)
    try:
        async for item in generator:
            yield item
    finally:
        reset_ai_override(token)


class AICallRequest(BaseModel):
    """与 Java AIService.AICallRequest 对应的请求体。"""
    module_name: str = Field(..., description="调用模块: prep/lecture/qa")
    scene_type: str = Field(..., description="场景: generate_lesson/create_ppt")
    prompt: str = Field(..., description="完整 prompt（Java 已拼入用户画像等上下文）")
    model_name: Optional[str] = Field(default=None, description="模型选择")
    max_tokens: Optional[int] = Field(default=None, description="最大输出 token")
    temperature: Optional[float] = Field(default=None, description="温度参数")
    extra: Optional[dict] = Field(default=None, description="扩展参数")
    # 用户自填的 AI 服务配置（baseUrl/apiKey/model），仅本次请求生效
    aiOverride: Optional[dict] = Field(default=None, description="用户自填 AI 配置")


class LessonPrepInternalRequest(BaseModel):
    """Java 端调用备课生成时的请求体。"""
    user_id: int = Field(..., description="用户ID")
    title: str = Field(..., description="备课标题")
    subject: str = Field(..., description="科目")
    grade: str = Field(..., description="年级")
    knowledge_point_ids: list[int] = Field(..., description="知识点ID列表")
    knowledge_point_names: list[str] = Field(default_factory=list, description="知识点名称列表（与ID一一对应）")
    teaching_goals: list[str] = Field(default_factory=list, description="教学目标")
    total_hours: int = Field(default=1, ge=1, le=10, description="课时数")
    style: str = Field(default="standard", description="备课风格")
    weak_point_ids: list[int] = Field(default_factory=list, description="薄弱知识点ID")
    user_profile_summary: Optional[str] = Field(
        default=None,
        description="用户画像摘要（Java 从 M6 获取后传入）"
    )
    # 用户自填的 AI 服务配置（baseUrl/apiKey/model），仅本次请求生效
    aiOverride: Optional[dict] = Field(default=None, description="用户自填 AI 配置")


@router.post("/ai/generate")
async def internal_ai_generate(request: AICallRequest):
    """非流式 AI 调用（通用入口）。"""
    logger.info(
        "Internal AI call: module=%s scene=%s model=%s",
        request.module_name, request.scene_type, request.model_name,
    )

    if request.module_name == "prep" and request.scene_type == "generate_lesson":
        extra = request.extra or {}
        prep_request = LessonPrepInternalRequest(
            user_id=extra.get("user_id", 0),
            title=extra.get("title", ""),
            subject=extra.get("subject", ""),
            grade=extra.get("grade", ""),
            knowledge_point_ids=extra.get("knowledge_point_ids", []),
            knowledge_point_names=extra.get("knowledge_point_names", []),
            teaching_goals=extra.get("teaching_goals", []),
            total_hours=extra.get("total_hours", 1),
            style=extra.get("style", "standard"),
            weak_point_ids=extra.get("weak_point_ids", []),
            user_profile_summary=extra.get("user_profile_summary"),
        )
        provider_config = _override_to_provider_config(request.aiOverride)
        token = set_ai_override(provider_config) if provider_config else None
        try:
            result = await _run_lesson_prep(prep_request)
        finally:
            if token is not None:
                reset_ai_override(token)
        return JSONResponse(content=result)

    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "error": f"Unsupported module/scene: {request.module_name}/{request.scene_type}",
        },
    )


@router.post("/ai/generate/stream")
async def internal_ai_generate_stream(request: AICallRequest):
    """流式 AI 调用（SSE），供 Java 透传给前端。"""
    if request.module_name == "prep" and request.scene_type in ("generate_lesson", "create_ppt"):
        extra = request.extra or {}
        prep_request = LessonPrepInternalRequest(
            user_id=extra.get("user_id", 0),
            title=extra.get("title", ""),
            subject=extra.get("subject", ""),
            grade=extra.get("grade", ""),
            knowledge_point_ids=extra.get("knowledge_point_ids", []),
            knowledge_point_names=extra.get("knowledge_point_names", []),
            teaching_goals=extra.get("teaching_goals", []),
            total_hours=extra.get("total_hours", 1),
            style=extra.get("style", "standard"),
            weak_point_ids=extra.get("weak_point_ids", []),
            user_profile_summary=extra.get("user_profile_summary"),
        )
        service = LessonPrepService()
        stream = service.run_stream(
            user_id=prep_request.user_id,
            title=prep_request.title,
            subject=prep_request.subject,
            grade=prep_request.grade,
            knowledge_point_ids=prep_request.knowledge_point_ids,
            teaching_goals=prep_request.teaching_goals,
            total_hours=prep_request.total_hours,
            style=prep_request.style,
            weak_point_ids=prep_request.weak_point_ids,
            user_profile_summary=prep_request.user_profile_summary,
        )
        provider_config = _override_to_provider_config(request.aiOverride)
        if provider_config:
            stream = _stream_with_override(stream, provider_config)
        return StreamingResponse(
            stream,
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
                "Connection": "keep-alive",
            },
        )

    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "error": f"Unsupported module/scene: {request.module_name}/{request.scene_type}",
        },
    )


@router.post("/ai/generate-lesson-prep")
async def internal_generate_lesson_prep(request: LessonPrepInternalRequest):
    """非流式备课生成 —— Java 调用此接口获取完整备课结果。"""
    provider_config = _override_to_provider_config(request.aiOverride)
    token = set_ai_override(provider_config) if provider_config else None
    try:
        result = await _run_lesson_prep(request)
    finally:
        if token is not None:
            reset_ai_override(token)
    return JSONResponse(content=result)


async def _run_lesson_prep(request: LessonPrepInternalRequest) -> dict:
    """统一调用三段备课管线，返回 dict 结果。"""
    logger.info(
        "Internal lesson prep: user=%d title=%s subject=%s grade=%s kps=%s",
        request.user_id, request.title, request.subject, request.grade,
        request.knowledge_point_ids,
    )

    ctx = PrepContext(
        user_id=request.user_id,
        title=request.title,
        subject=request.subject,
        grade=request.grade,
        knowledge_point_ids=request.knowledge_point_ids,
        knowledge_point_names=request.knowledge_point_names,
        teaching_goals=request.teaching_goals,
        total_hours=request.total_hours,
        style=request.style,
        weak_point_ids=request.weak_point_ids,
        user_profile_summary=request.user_profile_summary,
    )

    service = LessonPrepService()
    result = await service.generate_and_return(ctx)
    return result
