package com.treepeople.leapmindtts.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpStatus;

/**
 * 安全配置类
 * 配置密码加密和基础安全设置
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfig corsConfig;
    private final com.treepeople.leapmindtts.service.profile.security.M6SecurityErrorHandler m6SecurityErrorHandler;

    /**
     * 密码编码器Bean
     * 使用BCrypt算法进行密码加密
     *
     * @return BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置
     * 配置HTTP安全策略，禁用默认的Spring Security登录页面
     *
     * @param http HttpSecurity对象
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF保护，因为我们使用JWT
                .csrf(AbstractHttpConfigurer::disable)
                // 禁用默认表单登录和HTTP Basic
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 启用CORS配置
                .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
               // 配置会话管理为无状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 配置授权规则
                .authorizeHttpRequests(auth -> auth
                        // 允许OPTIONS预检请求（CORS需要）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 允许所有用户访问认证相关接口
                        .requestMatchers("/api/auth/**").permitAll()
                        // 允许所有用户访问教育阶段查询接口
                        .requestMatchers("/api/education/**").permitAll()
                        // 允许访问 API 文档
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        // 错误转发页无需认证（否则 404 路径会二次被拒返回 401）
                        .requestMatchers("/error").permitAll()
                        // 允许访问静态资源
                        .requestMatchers("/static/**", "/docs/**", "/*.html", "/*.js", "/*.css", "/admin/**", "/css/**", "/js/**", "/image/**").permitAll()
                        // 测试接口需要认证（生产环境应禁用）
                        .requestMatchers("/api/test/**").authenticated()
                        // 管理接口需要认证（具体权限由 @AdminRequired 注解控制）
                        .requestMatchers("/api/admin/**").authenticated()
                        // 管理后台审核接口需要认证
                        .requestMatchers("/admin/review/**").authenticated()
                        // 流式对话接口需要认证（防止匿名滥用 AI 资源）
                        .requestMatchers("/api/conversation/**").authenticated()
                        // 允许访问备课接口（PPT模板管理、备课内容管理）
                        .requestMatchers("/api/lesson-prep/**").permitAll()
                        // 允许访问即时讲课接口（M4 讲课生成与管理）
                        .requestMatchers("/api/teaching/**").permitAll()
                        // M6 事件上报接口：各模块后端直接调用（非前端发起，无用户 JWT）
                        // M1/M2/M3/M4/M7 后端服务通过此接口向画像引擎提交学习事件
                        .requestMatchers(HttpMethod.POST, "/api/user-profile/*/record-event").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/user-profile/*/batch-events").permitAll()
                        // Actuator 监控端点（本地/内网监控验证；生产建议收紧或加独立管理端口鉴权）
                        .requestMatchers("/actuator/**").permitAll()
                        // 语音合成和音频相关接口需要认证
                        .requestMatchers("/api/speech/**").authenticated()
                        // 语音对话接口需要认证
                        .requestMatchers("/api/voice-chat/**").authenticated()
                        // 本地音频地址允许直接播放，其余虚拟教师接口需要认证
                        .requestMatchers(HttpMethod.GET, "/api/virtual-teacher/audio/**").permitAll()
                        .requestMatchers("/api/virtual-teacher/**").authenticated()
                        // 课程相关接口需要认证
                        .requestMatchers("/api/courses/**").authenticated()
                        // 管理员接口需要认证（具体权限由@AdminRequired注解控制）
                        // 其他请求需要认证
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // M6 画像接口使用 M6 专属 401/403 格式；其余路径使用标准 401/403。
                        // 注意：defaultAuthenticationEntryPointFor 的映射数量必须 >= 2，
                        // 否则 Spring Security 会把唯一映射提升为全局默认 entry point。
                        .defaultAuthenticationEntryPointFor(m6SecurityErrorHandler,
                                new AntPathRequestMatcher("/api/user-profile/**"))
                        .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                AnyRequestMatcher.INSTANCE)
                        .defaultAccessDeniedHandlerFor(m6SecurityErrorHandler,
                                new AntPathRequestMatcher("/api/user-profile/**"))
                        .defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(),
                                AnyRequestMatcher.INSTANCE))
                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
