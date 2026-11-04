import base64
import logging
from typing import Optional

from fastapi import APIRouter
from pydantic import BaseModel, Field
from fastapi.responses import JSONResponse

from ..models.schemas import OCRProcessRequest, OCRProcessResponse
from ..services.ocr_processor import OCRProcessor
from ...core.config import ai_config

logger = logging.getLogger(__name__)
router = APIRouter(tags=["LeapMind - OCR"])
ocr_processor = OCRProcessor()

MAX_IMAGE_BYTES = 10 * 1024 * 1024
ALLOWED_MIME = {"image/png", "image/jpeg", "image/jpg", "image/webp", "image/bmp"}

VISION_OCR_PROMPT = (
    "请识别这张题目图片中的全部文字，严格按图片顺序逐行输出。\n"
    "要求：\n"
    "1. 题干、选项（A/B/C/D 等）、公式、上下标都要完整保留；\n"
    "2. 数学公式尽量写成 LaTeX 或清晰的线性形式，例如 f(x)=x*e^(-x)；\n"
    "3. 不要添加解释、不要总结、不要使用代码块；\n"
    "4. 如果图片中没有文字，只输出空字符串。"
)


class VisionOcrRequest(BaseModel):
    """用用户自填的视觉模型做识别（Java → Python）。"""
    imageBase64: str = Field(..., description="图片 base64（不含 data url 前缀）")
    mimeType: str = Field(default="image/png", description="图片 MIME 类型")
    aiOverride: Optional[dict] = Field(default=None, description="用户自填的 AI 配置")


@router.post("/api/internal/ai/ocr/process", response_model=OCRProcessResponse)
async def process_ocr(request: OCRProcessRequest):
    logger.info(f"OCR process request received, rawText length: {len(request.rawText)}")
    result = ocr_processor.process(request.rawText, request.subject)
    return result


@router.post("/api/internal/ai/ocr/vision")
async def vision_ocr(request: VisionOcrRequest):
    """使用用户自填的视觉模型识别题目图片。

    令牌只在本请求内存中使用，不写日志、不落盘。
    """
    override = request.aiOverride if isinstance(request.aiOverride, dict) else None
    if not override:
        return JSONResponse(status_code=400, content={"success": False, "error": "未提供 AI 服务配置"})

    base_url = override.get("baseUrl") or override.get("base_url")
    credential = override.get("apiKey") or override.get("api_key")
    model = override.get("model")
    if not base_url or not credential:
        return JSONResponse(status_code=400, content={"success": False, "error": "AI 服务配置不完整"})

    mime = (request.mimeType or "image/png").lower().strip()
    if mime not in ALLOWED_MIME:
        return JSONResponse(status_code=400, content={"success": False, "error": f"不支持的图片类型: {mime}"})

    try:
        image_bytes = base64.b64decode(request.imageBase64, validate=True)
    except Exception:
        return JSONResponse(status_code=400, content={"success": False, "error": "图片数据无法解析"})
    if not image_bytes:
        return JSONResponse(status_code=400, content={"success": False, "error": "图片内容为空"})
    if len(image_bytes) > MAX_IMAGE_BYTES:
        return JSONResponse(status_code=413, content={"success": False, "error": "图片过大（上限 10MB）"})

    try:
        from openai import AsyncOpenAI
    except ImportError:
        return JSONResponse(status_code=500, content={"success": False, "error": "服务端缺少 openai 依赖"})

    if not model:
        model = (ai_config.get_provider_config("openai") or {}).get("model")

    client = AsyncOpenAI(api_key=credential, base_url=base_url, timeout=120.0)
    data_url = f"data:{mime};base64,{request.imageBase64}"
    try:
        response = await client.chat.completions.create(
            model=model,
            messages=[{
                "role": "user",
                "content": [
                    {"type": "text", "text": VISION_OCR_PROMPT},
                    {"type": "image_url", "image_url": {"url": data_url}},
                ],
            }],
            temperature=0.1,
            max_tokens=2048,
        )
        text = (response.choices[0].message.content or "").strip()
    except Exception as e:
        logger.warning("视觉 OCR 调用失败: %s", type(e).__name__)
        return JSONResponse(status_code=502, content={"success": False, "error": "模型识别失败，请检查 AI 服务配置"})

    # 去掉模型偶尔包裹的代码块
    if text.startswith("```"):
        lines = [line for line in text.splitlines() if not line.strip().startswith("```")]
        text = "\n".join(lines).strip()

    return {"success": bool(text), "text": text, "provider": "vision", "model": model}
