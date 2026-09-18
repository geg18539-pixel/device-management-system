package com.yan.backend.interceptor;

import com.yan.backend.common.JwtUtil;
import com.yan.backend.common.UserContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * JWT 校验拦截器。
 *
 * <p>注意：这里实现的是 HandlerInterceptor 接口本身。
 * 不要照抄老教程写 {@code extends HandlerInterceptorAdapter} ——
 * 那个适配器类在 Spring Framework 6 就已经废弃、Spring Framework 7 里已被删除，
 * 我解包 spring-webmvc-7.0.9.jar 确认过它不存在了。虽然接口只有三个方法都有
 * default 实现，继承接口比继承类也更合适。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        // 浏览器的 CORS 预检请求不会带 Authorization 头，直接放行，
        // 否则跨域场景下所有请求都会先 401。
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER_NAME);
        if (header == null || !header.startsWith(TOKEN_PREFIX)) {
            writeUnauthorized(response, "未登录或登录已过期，请先登录");
            return false;
        }

        String token = header.substring(TOKEN_PREFIX.length()).trim();

        try {
            UserContext.set(jwtUtil.parseToken(token));
            return true;
        } catch (ExpiredJwtException e) {
            // 单独捕获过期，前端据此做"静默续期"或提示重新登录
            writeUnauthorized(response, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对、格式破损、issuer 不匹配等都归到这里
            writeUnauthorized(response, "登录凭证无效，请重新登录");
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 这一行不能省。
        // preHandle 里往 ThreadLocal 塞了登录用户，Tomcat 的线程是复用池化的，
        // 不清理的话下一个请求（哪怕是未登录的请求）会读到上一个请求的用户，
        // 造成越权；同时 ThreadLocal 持有的对象也无法被 GC，长期运行会内存泄漏。
        UserContext.clear();
    }

    /**
     * 输出 401 响应。
     *
     * <p>这里手写 JSON 字符串而不用 ObjectMapper 注入，是刻意为之：
     * Spring Boot 4 默认已切换到 Jackson 3（包名从 com.fasterxml.jackson
     * 变成 tools.jackson），而无参的 ObjectMapper 到底该注入哪个类型
     * 取决于 classpath 上具体有哪个 Jackson，容易写错版本导致编译问题。
     * 这里的三条消息都是固定文案、不含引号和反斜杠，直接拼接是安全的。
     * 等 5.3 需要序列化实体时，再统一确认 Jackson 版本。
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}
