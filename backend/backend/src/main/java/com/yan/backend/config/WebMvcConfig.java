package com.yan.backend.config;

import com.yan.backend.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册拦截器与放行规则。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 拦截所有 /api 接口
                .addPathPatterns("/api/**")
                // 以下路径放行
                .excludePathPatterns(
                        // 必须放行，否则"登录要带 token、拿 token 又要先登录"会死循环
                        "/api/auth/login",
                        // 之前做的前后端联调页用的接口，留着方便排查连通性。
                        // 它不涉及任何业务数据，正式环境可以删掉这行。
                        "/api/hello"
                );
    }
}
