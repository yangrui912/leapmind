package com.treepeople.leapmindtts.service.ai;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 用户提交的 AI 服务地址安全校验（防 SSRF）。
 *
 * <p>校验顺序：</p>
 * <ol>
 *   <li>语法：仅 http/https，必须有主机，长度受限；</li>
 *   <li>白名单（可选）：配置 {@code ai.proxy.allowed-hosts} 后，主机必须命中白名单；</li>
 *   <li>解析：主机名解析出的每个地址都必须是公网地址，禁止环回/私网/链路本地/保留/组播。</li>
 * </ol>
 *
 * <p>只有通过 {@link #requirePublicHttpUrl(String)} 返回的值才允许用于发起请求。</p>
 */
public final class UrlSafetyValidator {

    private static final int MAX_LENGTH = 2048;

    private UrlSafetyValidator() {
    }

    /**
     * 校验并归一化 AI 服务地址；不通过时抛出 {@link IllegalArgumentException}。
     */
    public static String requirePublicHttpUrl(String candidate) {
        return requirePublicHttpUrl(candidate, List.of());
    }

    /**
     * 校验并归一化 AI 服务地址（可选主机后缀白名单，为空表示不启用白名单）。
     */
    public static String requirePublicHttpUrl(String candidate, List<String> allowedHosts) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("AI 服务地址不能为空");
        }
        String value = candidate.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("AI 服务地址过长");
        }

        URI uri = parse(value);
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("AI 服务地址只支持 http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("AI 服务地址缺少主机名");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (isLocalHostname(normalizedHost)) {
            throw new IllegalArgumentException("AI 服务地址不允许指向本机或内网");
        }
        if (!allowedHosts.isEmpty() && !matchesAllowList(normalizedHost, allowedHosts)) {
            throw new IllegalArgumentException("AI 服务地址不在允许的主机白名单内");
        }

        InetAddress[] resolved = resolve(normalizedHost);
        for (InetAddress address : resolved) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException("AI 服务地址不允许指向本机、内网或保留地址");
            }
        }
        return uri.toString();
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("AI 服务地址格式不正确");
        }
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("AI 服务地址无法解析");
        }
    }

    private static boolean matchesAllowList(String host, List<String> allowedHosts) {
        for (String entry : allowedHosts) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String allowed = entry.trim().toLowerCase(Locale.ROOT);
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalHostname(String host) {
        return "localhost".equals(host)
                || host.startsWith("localhost.")
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal");
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            return isPublicIpv4(raw);
        }
        int first = raw[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) {
            // fc00::/7 唯一本地地址
            return false;
        }
        if (isIpv4Mapped(raw)) {
            return isPublicIpv4(Arrays.copyOfRange(raw, 12, 16));
        }
        return true;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF;
    }

    private static boolean isPublicIpv4(byte[] ip) {
        int a = ip[0] & 0xFF;
        int b = ip[1] & 0xFF;
        if (a == 0 || a == 10 || a == 127) {
            return false;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return false;
        }
        if (a == 192 && b == 168) {
            return false;
        }
        if (a == 169 && b == 254) {
            return false;
        }
        // 100.64.0.0/10 运营商级 NAT
        if (a == 100 && b >= 64 && b <= 127) {
            return false;
        }
        // 240.0.0.0/4 保留
        return a < 240;
    }
}
