import json
import logging
from typing import AsyncGenerator, Optional
from ...ai import get_ai_provider, AIProviderFactory, AIMessage, MessageRole
from ...core.config import ai_config

logger = logging.getLogger(__name__)


class LeapMindAIClient:
    def __init__(self, provider_name: Optional[str] = None, override: Optional[dict] = None):
        self.provider_name = provider_name or ai_config.default_ai_provider
        self.override = override if isinstance(override, dict) else None

    def _override_provider(self):
        """按用户自填配置创建一次性 provider；未填写时返回 None。

        只使用 OpenAI 兼容协议（base_url + 访问令牌 + 模型），
        配置仅存在于本次请求的内存中，不写入全局配置或磁盘。
        """
        if not self.override:
            return None
        base_url = self.override.get("baseUrl") or self.override.get("base_url")
        credential = self.override.get("apiKey") or self.override.get("api_key")
        model = self.override.get("model")
        if not base_url and not credential:
            return None
        try:
            config = dict(ai_config.get_provider_config("openai") or {})
        except Exception:  # 配置缺失时不应阻断用户自填场景
            config = {}
        if base_url:
            config["base_url"] = base_url
        if credential:
            config["api_key"] = credential
        if model:
            config["model"] = model
        return AIProviderFactory.create_provider("openai", config)

    @property
    def ai_provider(self):
        override_provider = self._override_provider()
        if override_provider is not None:
            return override_provider
        return get_ai_provider(self.provider_name)

    @property
    def model_override(self) -> Optional[str]:
        if not self.override:
            return None
        return self.override.get("model")

    async def stream_generate(self, prompt: str, **kwargs) -> AsyncGenerator[dict, None]:
        try:
            messages = [AIMessage(role=MessageRole.USER, content=prompt)]
            buffer = ""

            async for token in self.ai_provider.stream_chat_completion(
                messages=messages,
                max_tokens=kwargs.get("max_tokens", ai_config.max_tokens),
                temperature=kwargs.get("temperature", ai_config.temperature),
                **({"model": self.model_override} if self.model_override else {}),
            ):
                if not token:
                    continue
                buffer += token
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if line.startswith("{") and line.endswith("}"):
                        try:
                            yield json.loads(line)
                        except json.JSONDecodeError:
                            continue
                    elif line:
                        yield {"type": "content", "content": line}

            if buffer.strip():
                line = buffer.strip()
                if line.startswith("{") and line.endswith("}"):
                    try:
                        yield json.loads(line)
                    except json.JSONDecodeError:
                        yield {"type": "content", "content": line}
                elif line:
                    yield {"type": "content", "content": line}

        except Exception as e:
            logger.error(f"AI stream generation error: {e}")
            yield {"type": "error", "content": f"AI 生成失败: {str(e)}"}

    async def generate(self, prompt: str, **kwargs) -> str:
        try:
            messages = [AIMessage(role=MessageRole.USER, content=prompt)]
            response = await self.ai_provider.chat_completion(
                messages=messages,
                max_tokens=kwargs.get("max_tokens", min(ai_config.max_tokens, 4096)),
                temperature=kwargs.get("temperature", 0.3),
                **({"model": self.model_override} if self.model_override else {}),
            )
            return response.content
        except Exception as e:
            logger.error(f"AI generate error: {e}")
            raise
