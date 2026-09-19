package com.yan.backend.service;

import java.util.Set;

public interface PermissionService {

    /** 算出该用户拥有的全部权限标识（来自它所有「正常」状态角色下面挂的菜单/按钮） */
    Set<String> getPerms(Long userId);

    /**
     * 清空权限缓存。
     *
     * <p>任何会影响"谁能做什么"的改动之后都要调一次：给角色分配权限、给用户换角色、
     * 改菜单、删角色等等。清空是全局的而不是按用户 —— 因为一次角色权限变更可能
     * 影响该角色下的所有用户，逐个精确失效反而容易漏。
     * 代价只是下一次请求要回源查一次库，可以接受。
     */
    void evictAll();

    /** 仅仅是给日志/调试看：当前缓存了多少个用户的权限 */
    int cacheSize();

    /**
     * 在**当前事务提交之后**再清缓存。
     *
     * <p>不建议在事务里直接调 evictAll()：那时改动还没提交，
     * 如果紧接着有另一个请求进来查权限，会把**旧数据**重新读进缓存，
     * 结果就是"刚改完权限却不生效"，而且要等 TTL 过期才恢复。
     *
     * <p>如果当前没有事务，则立即清除。
     */
    void evictAfterCommit();
}
