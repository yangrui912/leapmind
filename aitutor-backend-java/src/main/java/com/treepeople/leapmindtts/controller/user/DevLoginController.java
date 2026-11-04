package com.treepeople.leapmindtts.controller.user;

import com.treepeople.leapmindtts.pojo.dto.LoginResponse;
import com.treepeople.leapmindtts.pojo.dto.UserLoginRequest;
import com.treepeople.leapmindtts.pojo.result.ApiResponse;
import com.treepeople.leapmindtts.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联调/演示环境的免登录入口。
 *
 * <p>默认关闭；仅在 {@code leapmind.dev-login.enabled=true} 时可用。
 * 账号与口令全部来自环境变量（{@code LEAPMIND_DEV_USERNAME} /
 * {@code LEAPMIND_DEV_PASSWORD}），源码中不保存任何凭据。</p>
 *
 * <p>用途：静态/演示部署时前端无需展示登录页，直接以预置演示账号进入，
 * 同时保留基于 userId 的功能（做题记录、薄弱点、学习画像）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserService userService;

    @Value("${leapmind.dev-login.enabled:false}")
    private boolean enabled;

    @Value("${leapmind.dev-login.username:}")
    private String demoUsername;

    @Value("${leapmind.dev-login.password:}")
    private String demoPassword;

    /**
     * 以预置演示账号换取 JWT，前端在无有效会话时自动调用。
     */
    @PostMapping("/dev-login")
    public ResponseEntity<ApiResponse<LoginResponse>> devLogin() {
        if (!enabled) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error(403, "演示登录未启用"));
        }
        if (demoUsername.isBlank() || demoPassword.isBlank()) {
            return ResponseEntity.status(503)
                    .body(ApiResponse.error(503, "演示账号未配置"));
        }

        UserLoginRequest request = new UserLoginRequest();
        request.setUsername(demoUsername.trim());
        request.setPassword(demoPassword);

        try {
            LoginResponse loginResponse = userService.login(request);
            log.info("演示免登录成功");
            return ResponseEntity.ok(ApiResponse.success(loginResponse, "登录成功"));
        } catch (Exception e) {
            log.warn("演示免登录失败: {}", e.getClass().getSimpleName());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(401, "演示账号不可用"));
        }
    }
}
