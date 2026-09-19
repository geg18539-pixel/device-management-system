package com.yan.backend.interceptor;

import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.JwtUtil;
import com.yan.backend.common.LoginUser;
import com.yan.backend.common.UserContext;
import com.yan.backend.service.PermissionService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 校验 + 角色鉴权拦截器。
 *
 * <p>注意：这里实现的是 HandlerInterceptor 接口本身。
 * 不要照抄老教程写 {@code extends HandlerInterceptorAdapter} ——
 * 那个适配器类在 Spring Framework 6 就已经废弃、Spring Framework 7 里已被删除。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 超级管理员角色标识。拥有这个角色的用户**绕过所有 @RequirePerm 检查**。
     *
     * <p>这是有意为之的取舍：不加绕过的话，每次新增一个按钮权限，
     * 都得记得去「角色管理」里给 admin 勾上，否则管理员自己反而点不动新功能 ——
     * 这种"把自己锁在门外"的情况在真实项目里非常常见。
     *
     * <p>代价是"给 admin 分配权限"在界面上更像一个展示。真正验证细粒度权限
     * 要用非超管账号（比如 operator）去看。
     */
    private static final String SUPER_ADMIN_ROLE = "admin";

    private final JwtUtil jwtUtil;
    private final PermissionService permissionService;

    public JwtInterceptor(JwtUtil jwtUtil, PermissionService permissionService) {
        this.jwtUtil = jwtUtil;
        this.permissionService = permissionService;
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
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "未登录或登录已过期，请先登录");
            return false;
        }

        String token = header.substring(TOKEN_PREFIX.length()).trim();

        LoginUser loginUser;
        try {
            loginUser = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已过期，请重新登录");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录凭证无效，请重新登录");
            return false;
        }

        // ★ 角色校验必须放在 UserContext.set() **之前**。
        //
        // 原因：preHandle 返回 false 时，Spring **不会**调用 afterCompletion，
        // 而清理 ThreadLocal 的代码就在 afterCompletion 里。
        // 如果先 set 再拒绝，登录信息就会留在当前线程上 ——
        // Tomcat 线程是池化复用的，下一个请求（哪怕是没登录的）会读到上一个用户的信息，
        // 既造成越权，也让 ThreadLocal 无法被 GC。
        if (!hasRequiredRole(handler, loginUser)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "没有权限执行该操作");
            return false;
        }

        // 权限校验同样必须在 UserContext.set() 之前
        if (!hasRequiredPerm(handler, loginUser)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "没有权限执行该操作，请联系管理员分配相应权限");
            return false;
        }

        UserContext.set(loginUser);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 见上面 preHandle 里的说明，这一行不能省
        UserContext.clear();
    }

    /**
     * 判断当前用户是否满足接口要求的角色。
     *
     * <p>没有标 @RequireRole 的接口一律放行（只要求登录）。
     */
    private boolean hasRequiredRole(Object handler, LoginUser loginUser) {
        // 静态资源等非 Controller 方法的请求，handler 不是 HandlerMethod，不做角色校验
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 先看方法上的注解，再看类上的 —— 方法优先，
        // 这样可以在一个整体受限的 Controller 里单独放开某个接口
        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            // getBeanType() 内部已经用 ClassUtils.getUserClass 处理过 CGLIB 代理。
            // 这点在本项目里是实际需要的：LogAspect 会给 Controller 生成代理类，
            // 直接反射代理类的注解会拿不到。
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        if (required == null || required.value().length == 0) {
            return true;
        }

        // 拥有其中任意一个角色即可
        Set<String> owned = loginUser.roles();
        if (owned == null || owned.isEmpty()) {
            return false;
        }
        for (String role : required.value()) {
            if (owned.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 细粒度权限校验：接口要求的权限标识，用户是否具备。
     *
     * <p>和角色校验的区别见 {@link RequirePerm} 的说明。简单说：
     * 角色校验问"你是不是管理员"，权限校验问"你有没有『新增用户』这个权限点"。
     * 后者存在数据库里，管理员可以在界面上调整，不用改代码。
     */
    private boolean hasRequiredPerm(Object handler, LoginUser loginUser) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePerm required = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (required == null || required.value().length == 0) {
            return true;
        }

        // 超管直接放行，理由见 SUPER_ADMIN_ROLE 的说明
        if (loginUser.roles() != null && loginUser.roles().contains(SUPER_ADMIN_ROLE)) {
            return true;
        }

        Set<String> owned = permissionService.getPerms(loginUser.userId());
        if (owned.isEmpty()) {
            return false;
        }
        for (String perm : required.value()) {
            if (owned.contains(perm)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 输出错误响应。
     *
     * <p>手写 JSON 而不是用 ObjectMapper 注入，原因见 5.1 时的说明：
     * Boot 4 的 Spring MVC 用的是 Jackson 3（tools.jackson），
     * 而 classpath 上同时存在 Jackson 2（jjwt 带进来的），注入时容易选错类型。
     * 这里几条消息都是固定文案、不含引号和反斜杠，直接拼接是安全的。
     */
    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
