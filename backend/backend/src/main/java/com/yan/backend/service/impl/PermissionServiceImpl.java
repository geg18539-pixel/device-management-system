package com.yan.backend.service.impl;

import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限标识的计算与缓存。
 *
 * <p>每个受权限保护的请求都要知道"这个用户有哪些权限"。直接查库的话，
 * 每次请求都要走一遍 用户→角色→菜单 的 JOIN；而权限数据变动极少，
 * 所以放进进程内缓存。
 *
 * <p>缓存策略：**短 TTL + 主动清除**双保险。
 * <ul>
 *   <li>TTL 5 分钟：兜底，防止某条改动路径忘了调 evictAll 导致缓存永远不更新。</li>
 *   <li>权限变更时 evictAll()：保证"管理员刚勾完权限，下一次请求就生效"，
 *       而不是等 5 分钟。这是用户能直观感知到的体验差异。</li>
 * </ul>
 *
 * <p>没用 Caffeine/Guava 之类的外部缓存库：需求就是"一个带过期时间的 Map"，
 * 多引一个依赖不划算。真要上分布式部署时再换成 Redis。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);

    /** 缓存有效期：5 分钟 */
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private record CachedPerms(Set<String> perms, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final SysMenuRepository sysMenuRepository;
    private final Map<Long, CachedPerms> cache = new ConcurrentHashMap<>();

    public PermissionServiceImpl(SysMenuRepository sysMenuRepository) {
        this.sysMenuRepository = sysMenuRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getPerms(Long userId) {
        if (userId == null) {
            return Set.of();
        }

        CachedPerms cached = cache.get(userId);
        if (cached != null && !cached.expired()) {
            return cached.perms();
        }

        List<String> perms = sysMenuRepository.findPermsByUserId(userId);
        Set<String> result = perms == null ? Set.of() : new HashSet<>(perms);

        cache.put(userId, new CachedPerms(result, System.currentTimeMillis() + TTL_MILLIS));
        return result;
    }

    @Override
    public void evictAll() {
        int size = cache.size();
        cache.clear();
        if (size > 0) {
            log.info("权限缓存已清除（原来缓存了 {} 个用户的权限），下次请求会重新查库", size);
        }
    }

    @Override
    public int cacheSize() {
        return cache.size();
    }

    @Override
    public void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAll();
                }
            });
        } else {
            evictAll();
        }
    }
}
