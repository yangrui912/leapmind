"""
M4 讲课后端 API 端点
POST /api/ai/stream-generate-teaching  — SSE 流式讲课生成（Java 透传 → 前端）
POST /api/ai/generate-teaching          — 同步讲课生成

对应 Java AISseServiceImpl 的调用：
  - streamGenerateTeachingContent → /api/ai/stream-generate-teaching
  - generateTeachingContent        → /api/ai/generate-teaching
"""
import json
import logging
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel, Field

from ..services.lecture_generation_service import LectureGenerationService

router = APIRouter(prefix="/api/ai", tags=["M4 Teaching AI"])
logger = logging.getLogger(__name__)


class TeachingGenerateRequest(BaseModel):
    course_id: str = Field(default="", description="课程ID")
    source_text: str = Field(default="", description="教学内容文本")
    user_profile: Optional[dict] = Field(default=None, description="用户画像")
    # 用户自填的 AI 服务配置（baseUrl/apiKey/model），仅本次请求生效
    aiOverride: Optional[dict] = Field(default=None, description="用户自填 AI 配置")


@router.post("/generate-teaching")
async def generate_teaching(request: TeachingGenerateRequest):
    """同步生成讲课内容（非流式）"""
    logger.info("M4 sync generate: course_id=%s", request.course_id)
    profile = request.user_profile or {}
    grade = profile.get("grade", "")
    weak_list = profile.get("weakPoints", [])
    weak_points = ", ".join(weak_list) if weak_list else ""

    from ..ai.providers import set_ai_override
    if request.aiOverride and isinstance(request.aiOverride, dict):
        cfg = {k: v for k, v in {
            "base_url": request.aiOverride.get("baseUrl") or request.aiOverride.get("base_url"),
            "api_key": request.aiOverride.get("apiKey") or request.aiOverride.get("api_key"),
            "model": request.aiOverride.get("model"),
        }.items() if v}
        if cfg:
            set_ai_override(cfg)

    service = LectureGenerationService()
    slides = []
    async for event_str in service.run_stream(
        source_text=request.source_text,
        course_id=request.course_id,
        grade=grade,
        weak_points=weak_points,
    ):
        if "data:" in event_str:
            data_str = event_str.split("data:", 1)[1].strip()
            try:
                data = json.loads(data_str)
                if data.get("type") == "slide" and data.get("slide"):
                    slides.append(data["slide"])
            except json.JSONDecodeError:
                pass

    return JSONResponse(content={
        "success": True,
        "slides": slides,
        "totalPages": len(slides),
    })


@router.post("/stream-generate-teaching")
async def stream_generate_teaching(request: TeachingGenerateRequest):
    """SSE 流式生成讲课内容"""
    logger.info("M4 SSE generate: course_id=%s text_len=%d", request.course_id, len(request.source_text))

    profile = request.user_profile or {}
    grade = profile.get("grade", "")
    weak_list = profile.get("weakPoints", [])
    weak_points = ", ".join(weak_list) if weak_list else ""

    from ..ai.providers import set_ai_override
    if request.aiOverride and isinstance(request.aiOverride, dict):
        cfg = {k: v for k, v in {
            "base_url": request.aiOverride.get("baseUrl") or request.aiOverride.get("base_url"),
            "api_key": request.aiOverride.get("apiKey") or request.aiOverride.get("api_key"),
            "model": request.aiOverride.get("model"),
        }.items() if v}
        if cfg:
            # 在当前请求上下文中设置；不使用 token/reset（StreamingResponse 的
            # generator 运行在子上下文，跨上下文 reset 会抛 ValueError）。
            # 每个 HTTP 请求拥有独立的 context 副本，请求结束即随之消亡。
            set_ai_override(cfg)

    service = LectureGenerationService()

    async def stream():
        async for event_str in service.run_stream(
            source_text=request.source_text,
            course_id=request.course_id,
            grade=grade,
            weak_points=weak_points,
        ):
            yield event_str

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )
