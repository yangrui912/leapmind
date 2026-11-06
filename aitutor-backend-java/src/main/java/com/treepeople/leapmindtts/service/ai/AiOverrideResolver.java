package com.treepeople.leapmindtts.service.ai;

import com.treepeople.leapmindtts.pojo.dto.AiOverride;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 从当前 HTTP 请求头解析用户自填的 AI 服务配置。
 *
 * <p>安全模型（默认关闭，需运维显式开启）：</p>
 * <ol>
 *   <li>{@code ai.proxy.user-config-enabled=false} 时不接受任何用户提交的地址与令牌；</li>
 *   <li>开启后，地址必须通过 {@link UrlSafetyValidator} 的公网地址校验；</li>
 *   <li>配置 {@code ai.proxy.allowed-hosts} 后只放行白名单主机。</li>
 * </ol>
 *
 * <p>令牌只做内存透传：不写日志、不落库、不写文件。</p>
 */
@Slf4j
@Component
public class AiOverrideResolver {

    private static final String HEADER_PREFIX = "X-Ai-";
    private static final String ADDRESS_HEADER = HEADER_PREFIX + "Base-Url";
    private static final String CREDENTIAL_HEADER = HEADER_PREFIX + "Token";
    private static final String MODEL_HEADER = HEADER_PREFIX + "Model";
    private static final String VISION_MODEL_HEADER = HEADER_PREFIX + "Vision-Model";

    private final boolean userConfigEnabled;
    private final List<String> allowedHosts;

    public AiOverrideResolver(
            @Value("${ai.proxy.user-config-enabled:false}") boolean userConfigEnabled,
            @Value("${ai.proxy.allowed-hosts:}") String allowedHostsConfig) {
        this.userConfigEnabled = userConfigEnabled;
        this.allowedHosts = splitHosts(allowedHostsConfig);
    }

    /**
     * 读取当前请求中的用户 AI 配置；未提供或功能未开启时返回 null（走服务端默认配置）。
     */
    public AiOverride current() {
        if (!userConfigEnabled) {
            return null;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String rawAddress = request.getHeader(ADDRESS_HEADER);
        String rawCredential = request.getHeader(CREDENTIAL_HEADER);
        String rawModel = request.getHeader(MODEL_HEADER);
        String rawVisionModel = request.getHeader(VISION_MODEL_HEADER);

        String address = trimToNull(rawAddress);
        String credential = trimToNull(rawCredential);
        String model = trimToNull(rawModel);
        String visionModel = trimToNull(rawVisionModel);

        if (address == null && credential == null && model == null && visionModel == null) {
            return null;
        }
        if (address == null || credential == null) {
            throw new IllegalArgumentException("自定义 AI 配置不完整：请同时填写服务地址和访问令牌");
        }

        AiOverride override = new AiOverride();
        override.setBaseUrl(UrlSafetyValidator.requirePublicHttpUrl(address, allowedHosts));
        override.setApiKey(credential);
        override.setModel(model);
        override.setVisionModel(visionModel);
        log.debug("已启用用户自填 AI 配置");
        return override;
    }

    private static List<String> splitHosts(String config) {
        List<String> hosts = new ArrayList<>();
        if (config == null || config.isBlank()) {
            return hosts;
        }
        for (String part : config.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                hosts.add(trimmed);
            }
        }
        return hosts;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
