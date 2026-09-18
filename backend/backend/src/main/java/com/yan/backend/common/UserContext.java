package com.yan.backend.common;

/**
 * 用 ThreadLocal 保存当前请求的登录用户。
 *
 * <p>这是"轻量版"认证方案：不引入 Spring Security 的过滤器链，只在拦截器里
 * 解析 JWT 后把用户塞进来，业务代码随时能取到当前操作人（后面 5.3 的
 * AOP 日志就要用它记录"谁操作了什么"）。
 *
 * <p><b>必须成对使用</b>：拦截器 preHandle 里 set，afterCompletion 里 clear。
 * 只 set 不 clear 会有两个后果：
 * <ol>
 *   <li>Tomcat 用线程池复用线程，下一个请求可能读到上一个请求的用户 —— 串号；</li>
 *   <li>ThreadLocal 持有的对象不会被回收，长期运行下是内存泄漏。</li>
 * </ol>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
        // 工具类，不允许实例化
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    /** 未登录时返回 null，调用方需要自己判空 */
    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    /** 清理，必须在请求结束时调用 */
    public static void clear() {
        HOLDER.remove();
    }
}
