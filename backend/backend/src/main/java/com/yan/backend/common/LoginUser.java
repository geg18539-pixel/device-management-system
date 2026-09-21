package com.yan.backend.common;

import java.util.Set;

/**
 * 当前登录用户的轻量信息。
 *
 * <p>只包含从 JWT 里能直接解析出来的字段，不查数据库 ——
 * 这样拦截器校验 token 时不需要访问 DB，性能好，也避免了
 * open-in-view=false 下的一些懒加载问题。
 *
 * <p>用 record 是因为它天然不可变，放进 ThreadLocal 后不用担心被下游代码改掉。
 *
 * <p><b>注意 perms 不在这个对象里</b>：权限点数量会随功能增长，
 * 全塞进 token 会让它越来越大（而且改权限后旧 token 里的集合就过期了）。
 * 权限由 {@code PermissionService} 单独带缓存地查，
 * 需要给前端时在 Controller 层组装成 {@code CurrentUserVO}。
 */
public record LoginUser(Long userId,
                        String username,
                        String nickname,
                        Set<String> roles,
                        boolean mustChangePassword) {

    /** 是否是超级管理员。超管绕过所有 @RequirePerm 检查，理由见 JwtInterceptor */
    public boolean isSuperAdmin() {
        return roles != null && roles.contains("admin");
    }
}
