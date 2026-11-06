package com.treepeople.leapmindtts.controller.lesson;

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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 备课生成 — 直接透传 Python 的流式接口。
 *
 * <p>Python 端（landppt）已实现完整的备课三段管线并自行落库，
 * Java 只负责鉴权、注入用户自填的 AI 配置、以及 SSE 转发。
 * 这样可以原样使用 Python 的 {@code parallel} 并发能力，避免 Java 侧再维护一份逻辑。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/lesson-prep")
@RequiredArgsConstructor
public class LessonPrepController {

    private final WebClient.Builder webClientBuilder;
    private final AiOverrideResolver aiOverrideResolver;

    /**
     * 备课接口在 landppt 主服务（8000），不是 M2 服务（8001）。
     */
    @Value("${python.lesson-prep.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    /** 复用一个 WebClient，避免每次请求新建连接池导致连接被重置 */
    private WebClient client;

    @jakarta.annotation.PostConstruct
    void initClient() {
        this.client = webClientBuilder.build();
    }

    /**
     * 流式生成备课（SSE）。
     *
     * <p>事件：outline → section × N → done（含 prep_id）；异常时 error。</p>
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@RequestBody Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        AiOverride override = aiOverrideResolver.current();
        if (override != null) {
            payload.put("aiOverride", override);
        }
        // 默认开启并行（前端未显式指定时），明显缩短多课时生成耗时
        payload.putIfAbsent("parallel", true);

        log.info("[M5] 备课生成请求: title={}, parallel={}",
                payload.get("title"), payload.get("parallel"));

        return client.post()
                .uri(pythonBaseUrl + "/api/lesson-prep/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(raw -> log.info("[M5] 收到片段({} 字): {}",
                        raw == null ? 0 : raw.length(),
                        raw == null ? "" : raw.substring(0, Math.min(120, raw.length()))))
                .map(event -> {
                    // 与 M2 相同的成熟写法：bodyToFlux(String) 拿到的是 data 内容（可能是纯 JSON，
                    // 也可能带 Python 原始的 "data:" 前缀）；Spring MVC 写响应时会统一加 "data:"，
                    // 因此这里必须剥掉已有前缀，否则出现 "data:data:"，前端 JSON.parse 全失败。
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
                .timeout(Duration.ofMinutes(10))
                .onErrorResume(err -> {
                    log.error("[M5] 备课生成失败: {} / {}", err.getClass().getName(), err.getMessage(), err);
                    String message = sanitize(err.getMessage());
                    // Spring 会自动加 "data:" 前缀，这里只返回 JSON 本体
                    return Flux.just("{\"type\":\"error\",\"message\":\"" + escapeJson(message) + "\"}");
                });
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "备课生成失败";
        }
        String cleaned = message.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) : cleaned;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
