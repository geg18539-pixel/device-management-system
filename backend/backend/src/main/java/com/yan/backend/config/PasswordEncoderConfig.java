package com.yan.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器。
 *
 * <p>这里只引入了 spring-security-crypto 这一个 jar，它是独立的加密工具库，
 * **不会**带来 Spring Security 的过滤器和自动配置 —— 也就是说不会出现
 * "所有请求都被拦去登录页"那种情况。完整的 Spring Security 过滤器链
 * 需要 spring-boot-starter-security，本项目刻意没有引入。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
