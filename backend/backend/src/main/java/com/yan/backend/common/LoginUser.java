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
 */
public record LoginUser(Long userId, String username, String nickname, Set<String> roles) {
}
