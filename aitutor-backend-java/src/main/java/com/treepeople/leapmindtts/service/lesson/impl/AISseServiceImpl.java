package com.treepeople.leapmindtts.service.lesson.impl;

import com.treepeople.leapmindtts.service.lesson.AISseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI SSE 流式讲课生成实现（许沣睿）
 * 通过 WebClient 代理转发到 AI 服务
 */
@Slf4j
@Service
public class AISseServiceImpl implements AISseService {

    private final WebClient webClient;
    private final com.treepeople.leapmindtts.service.ai.AiOverrideResolver aiOverrideResolver;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public AISseServiceImpl(WebClient webClient,
                            com.treepeople.leapmindtts.service.ai.AiOverrideResolver aiOverrideResolver,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.aiOverrideResolver = aiOverrideResolver;
        this.objectMapper = objectMapper;
    }

    @Value("${ai.teaching.service-url:http://localhost:8000}")
    private String aiTeachingServiceUrl;

    /** 组装带用户自填 AI 配置的请求体（未配置时不包含该字段） */
    private Map<String, Object> buildBody(String courseId, String sourceText, Map<String, Object> userProfile) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("course_id", courseId);
        body.put("source_text", sourceText != null ? sourceText : "");
        body.put("user_profile", userProfile != null ? userProfile : Map.of());
        var override = aiOverrideResolver.current();
        if (override != null) {
            body.put("aiOverride", override);
        }
        return body;
    }

    @Override
    public String generateTeachingContent(String courseId, String sourceText, Map<String, Object> userProfile) {
        Map<String, Object> requestBody = buildBody(courseId, sourceText, userProfile);

        try {
            String result = webClient.post()
                    .uri(aiTeachingServiceUrl + "/api/ai/generate-teaching")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("AI讲课内容生成完成: courseId={}, contentLength={}",
                    courseId, result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("AI讲课内容生成失败: courseId={}", courseId, e);
            throw new RuntimeException("AI生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> streamGenerateTeachingContent(String courseId, String sourceText,
                                                      Map<String, Object> userProfile) {
        Map<String, Object> requestBody = buildBody(courseId, sourceText, userProfile);

        return webClient.post()
                .uri(aiTeachingServiceUrl + "/api/ai/stream-generate-teaching")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .map(event -> {
                    // Python 的 JSON 行被 WebClient 反序列化成了 Map；
                    // String.valueOf(map) 会输出 "{type=outline,...}" 这种 Java 格式，
                    // 前端 JSON.parse 全部失败。必须重新序列化为标准 JSON。
                    Object data = event.data();
                    if (data == null) return "";
                    if (data instanceof String s && !s.isEmpty()) return s;
                    try {
                        return objectMapper.writeValueAsString(data);
                    } catch (Exception e) {
                        log.warn("SSE data 序列化失败: {}", e.getMessage());
                        return "";
                    }
                })
                .filter(s -> !s.isEmpty())
                .doOnNext(chunk -> log.debug("SSE chunk received: {} chars", chunk.length()))
                .doOnError(error -> log.error("SSE流式传输出错: courseId={}", courseId, error))
                .doOnComplete(() -> log.info("SSE流式传输完成: courseId={}", courseId))
                // AI 服务不可用/出错时，不能把 Flux 错误抛给 SSE 通道（会导致响应被
                // 认证层拦截返回 401），必须回退为 error 事件 + 正常结束。
                .onErrorResume(error -> Flux.just(
                        "{\"type\":\"error\",\"message\":\"AI生成服务暂不可用，请稍后重试\"}"));
    }
}
