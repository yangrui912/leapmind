package com.treepeople.leapmindtts.controller.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treepeople.leapmindtts.pojo.dto.AiOverride;
import com.treepeople.leapmindtts.service.ai.AiOverrideResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M5 备课辅助接口透传（教学目标 / 教学过程 / PPT 结构）。
 *
 * <p>这三个能力在 Python 端（landppt）已完整实现，但此前 Java 没有任何转发端点，
 * 前端调用一律 404。这里统一透传，并把用户自填的 AI 配置一并带过去。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep")
@RequiredArgsConstructor
public class LessonPrepAuxController {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiOverrideResolver aiOverrideResolver;

    /**
     * 备课接口在 landppt 主服务（8000），不是 M2 服务（8001）。
     * 注意：python.service.base-url 指向 8001，不能复用。
     */
    @Value("${python.lesson-prep.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    /**
     * AI 生成教学目标（3~5 条）
     */
    @PostMapping("/generate-goals")
    public Mono<Object> generateGoals(@RequestBody Map<String, Object> body) {
        return proxyJson("/api/lesson-prep/generate-goals", body, Duration.ofSeconds(180));
    }

    /**
     * AI 生成教学过程（4~6 个环节）
     */
    @PostMapping("/generate-process")
    public Mono<Object> generateProcess(@RequestBody Map<String, Object> body) {
        return proxyJson("/api/lesson-prep/generate-process", body, Duration.ofSeconds(180));
    }

    /**
     * 基于已有备课内容生成 PPT 结构
     */
    @PostMapping("/generate-ppt")
    public Mono<Object> generatePpt(@RequestBody Map<String, Object> body) {
        return proxyJson("/api/lesson-prep/generate-ppt", body, Duration.ofSeconds(300));
    }

    /**
     * 流式生成备课（SSE 透传，支持 parallel 加速）
     */
    @PostMapping(value = "/generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(@RequestBody Map<String, Object> body) {
        return proxySse("/api/lesson-prep/generate", body, Duration.ofMinutes(10));
    }

    // ---------------------------------------------------------------- 内部实现

    private Mono<Object> proxyJson(String path, Map<String, Object> body, Duration timeout) {
        Map<String, Object> payload = withAiOverride(body);
        log.info("[M5 透传] POST {}", path);

        return webClientBuilder.build()
                .post()
                .uri(pythonBaseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Object>() {
                })
                // Python 侧偶发连接被重置（Reactor Netty 连接池空闲回收），重试一次即可
                .retryWhen(reactor.util.retry.Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(err -> err instanceof org.springframework.web.reactive.function.client.WebClientRequestException
                                || err instanceof java.util.concurrent.TimeoutException))
                .timeout(timeout)
                .onErrorResume(err -> {
                    log.error("[M5 透传] {} 失败: {} / {}", path,
                            err.getClass().getSimpleName(), err.getMessage(), err);
                    Map<String, Object> error = new LinkedHashMap<>();
                    String detail = err.getMessage();
                    if (detail == null || detail.isBlank()) {
                        detail = err.getClass().getSimpleName();
                        if (err.getCause() != null && err.getCause().getMessage() != null) {
                            detail += ": " + err.getCause().getMessage();
                        }
                    }
                    error.put("detail", sanitize(detail));
                    return Mono.just(error);
                });
    }

    private Flux<String> proxySse(String path, Map<String, Object> body, Duration timeout) {
        Map<String, Object> payload = withAiOverride(body);
        log.info("[M5 透传] SSE {}", path);

        return webClientBuilder.build()
                .post()
                .uri(pythonBaseUrl + path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .map(event -> {
                    if (event == null) {
                        return "";
                    }
                    String trimmed = event.trim();
                    if (trimmed.startsWith("data:")) {
                        trimmed = trimmed.substring(5).trim();
                    }
                    return trimmed;
                })
                .filter(s -> !s.isEmpty())
                .timeout(timeout)
                .onErrorResume(err -> {
                    log.error("[M5 透传] SSE {} 失败: {}", path, err.toString());
                    String message = sanitize(err.getMessage());
                    return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(message) + "\"}");
                });
    }

    /**
     * 把用户自填的 AI 配置注入请求体（键名与 Python 端一致：aiOverride）
     */
    private Map<String, Object> withAiOverride(Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        AiOverride override = aiOverrideResolver.current();
        if (override != null) {
            payload.put("aiOverride", override);
        }
        return payload;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "AI 服务调用失败";
        }
        String cleaned = message.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
