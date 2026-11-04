package com.treepeople.leapmindtts.controller.ai;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.treepeople.leapmindtts.pojo.dto.AiOverride;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.ai.AiOverrideResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户自填 AI 配置的连通性自检。
 *
 * <p>服务地址已经过 {@link AiOverrideResolver} 的 SSRF 校验（仅 http/https、
 * 拒绝本机/私网/保留地址），这里只使用校验后的地址发起一次 /models 探测。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConfigTestController {

    private final AiOverrideResolver aiOverrideResolver;
    private final WebClient.Builder webClientBuilder;

    /**
     * 用当前请求头中的 AI 配置探测服务是否可用，并返回可用模型列表。
     */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test() {
        AiOverride override = aiOverrideResolver.current();
        if (override == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "尚未填写 AI 服务配置"));
        }

        String endpoint = trimTrailingSlash(override.getBaseUrl()) + "/models";
        try {
            String body = webClientBuilder.build()
                    .get()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + override.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("models", extractModels(body));
            result.put("reachable", true);
            return ResponseEntity.ok(ApiResponse.success(result, "连接成功"));
        } catch (Exception e) {
            String detail = sanitize(e.getMessage());
            log.warn("AI 配置自检失败: {}", detail);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reachable", false);
            result.put("error", detail);
            return ResponseEntity.status(502)
                    .body(new ApiResponse<>(502, "连接失败：" + detail, result, System.currentTimeMillis()));
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static List<String> extractModels(String body) {
        List<String> models = new ArrayList<>();
        try {
            JSONObject json = JSONUtil.parseObj(body);
            JSONArray data = json.getJSONArray("data");
            if (data != null) {
                for (Object item : data) {
                    if (item instanceof JSONObject node) {
                        String id = node.getStr("id");
                        if (id != null && !id.isBlank()) {
                            models.add(id);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 响应不是标准 /models 结构时，返回空列表即可
        }
        return models;
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        String cleaned = message.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "sk-***")
                .replaceAll("Bearer\\s+[A-Za-z0-9_\\-\\.]{8,}", "Bearer ***");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) + "…" : cleaned;
    }
}
