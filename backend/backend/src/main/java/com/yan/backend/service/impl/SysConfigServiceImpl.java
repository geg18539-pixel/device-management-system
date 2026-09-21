package com.yan.backend.service.impl;

import com.yan.backend.entity.SysConfig;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysConfigRepository;
import com.yan.backend.service.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统参数的读取与管理。
 *
 * <p><b>为什么整表缓存在内存里？</b>
 * 这些参数在**每个请求**里都会被读（密码有效期在登录时、附件大小在每次上传时、
 * 预警天数在每次打开看板时）。每次都查库的话，等于给最热的几条路径都加了一次
 * 数据库往返。而参数总共就几十行、改动极少，整表读进 Map 里最划算。
 *
 * <p>缓存策略和 PermissionService 保持一致：**短 TTL + 主动清除**双保险。
 * TTL 是兜底（防止某条改动路径忘了清缓存），主动清除保证"改完立刻生效"。
 */
@Service
@Transactional(readOnly = true)
public class SysConfigServiceImpl implements SysConfigService {

    private static final Logger log = LoggerFactory.getLogger(SysConfigServiceImpl.class);

    /** 缓存有效期：5 分钟 */
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final SysConfigRepository sysConfigRepository;

    /** key -> value。volatile，因为可能是不同线程先后来填 / 读它 */
    private volatile Map<String, String> cache;
    private volatile long expiresAt;

    public SysConfigServiceImpl(SysConfigRepository sysConfigRepository) {
        this.sysConfigRepository = sysConfigRepository;
    }

    // ============================================================
    // 读
    // ============================================================

    @Override
    public String getString(String key, String defaultValue) {
        String value = snapshot().get(key);
        // 库里存了空串也当作"没配"，用默认值 —— 页面上清空一个输入框
        // 表达的语义是"恢复默认"，而不是"设成空字符串"
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        String raw = getString(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // 不抛异常：一个参数被手工改坏了，不该让登录/上传这些功能直接不可用。
            // 回退默认值 + 打警告，运维看日志能发现
            log.warn("系统参数 {} 的值「{}」不是合法整数，已回退默认值 {}",
                    key, raw, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        String raw = getString(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("系统参数 {} 的值「{}」不是合法整数，已回退默认值 {}",
                    key, raw, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = getString(key, null);
        if (raw == null) {
            return defaultValue;
        }
        String v = raw.trim();
        if ("true".equalsIgnoreCase(v) || "1".equals(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v) || "0".equals(v)) {
            return false;
        }
        log.warn("系统参数 {} 的值「{}」不是合法布尔值，已回退默认值 {}",
                key, raw, defaultValue);
        return defaultValue;
    }

    /**
     * 取一份可用的缓存快照。过期或为空时重新加载。
     *
     * <p>加载失败（比如表还没建出来）时返回空 Map 而不是抛异常 ——
     * 这样所有读取都会走各自的默认值，功能降级但可用。
     * 不要在这里抛异常：这个方法被登录、上传这些核心路径调用。
     */
    private Map<String, String> snapshot() {
        Map<String, String> current = cache;
        if (current != null && System.currentTimeMillis() < expiresAt) {
            return current;
        }
        synchronized (this) {
            if (cache != null && System.currentTimeMillis() < expiresAt) {
                return cache;
            }
            Map<String, String> loaded = new HashMap<>();
            try {
                for (SysConfig config : sysConfigRepository.findAll()) {
                    if (StringUtils.hasText(config.getConfigKey())) {
                        loaded.put(config.getConfigKey(), config.getConfigValue());
                    }
                }
            } catch (Exception e) {
                log.warn("读取系统参数失败，本次全部使用默认值：{}", e.getMessage());
            }
            cache = loaded;
            expiresAt = System.currentTimeMillis() + TTL_MILLIS;
            return loaded;
        }
    }

    // ============================================================
    // 管理
    // ============================================================

    @Override
    public List<SysConfig> listAll() {
        return sysConfigRepository.findAllByOrderByConfigGroupAscSortOrderAscIdAsc();
    }

    @Override
    @Transactional
    public SysConfig create(SysConfig config) {
        if (sysConfigRepository.existsByConfigKey(config.getConfigKey())) {
            throw new IllegalStateException("参数键已存在：" + config.getConfigKey());
        }
        validateValue(config.getValueType(), config.getConfigValue(), config.getConfigName());
        // 界面新增的参数一律不是内置项 —— 内置项是种子数据建的，对应代码里的读取点
        config.setBuiltIn(Boolean.FALSE);
        SysConfig saved = sysConfigRepository.save(config);
        evictAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public SysConfig updateValue(String key, String value) {
        SysConfig config = sysConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("参数不存在，key = " + key));

        validateValue(config.getValueType(), value, config.getConfigName());
        config.setConfigValue(value);
        SysConfig saved = sysConfigRepository.save(config);
        evictAfterCommit();
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysConfig config = sysConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("参数不存在，id = " + id));

        // 内置项不允许删。删掉不会报错，只会悄悄用回 yml 默认值，
        // 管理员会以为"我明明配过、怎么不生效" —— 这种事后的困惑比当场报错难查得多
        if (Boolean.TRUE.equals(config.getBuiltIn())) {
            throw new IllegalStateException("「" + config.getConfigName()
                    + "」是内置参数，不能删除（可以修改它的值）");
        }
        sysConfigRepository.delete(config);
        evictAfterCommit();
    }

    @Override
    public void evictCache() {
        cache = null;
        expiresAt = 0;
    }

    /**
     * 事务提交后再清缓存。
     *
     * <p>如果在这里直接清，事务还没提交，紧接着另一个请求读参数会把**旧值**读回缓存，
     * 表现为"刚改完却不生效，要等一会儿才好"。这个坑在权限缓存那边已经踩过一次。
     */
    private void evictAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictCache();
                }
            });
        } else {
            evictCache();
        }
    }

    /**
     * 保存前校验值能不能按声明的类型解析。
     *
     * <p>不校验的话，把「密码有效期」写成 "abc" 也能保存成功，
     * 之后登录时会**静默回退到默认值** —— 管理员改了个值却看不出任何效果，
     * 只能去翻日志才能发现。这里当场拒绝要清楚得多。
     */
    private void validateValue(String valueType, String value, String configName) {
        if (!StringUtils.hasText(value) || SysConfig.TYPE_STRING.equals(valueType)) {
            return;
        }
        String v = value.trim();
        if (SysConfig.TYPE_NUMBER.equals(valueType)) {
            try {
                Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "「" + configName + "」需要填写整数，当前填的是：" + value);
            }
        } else if (SysConfig.TYPE_BOOLEAN.equals(valueType)) {
            if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)
                    && !"1".equals(v) && !"0".equals(v)) {
                throw new IllegalArgumentException(
                        "「" + configName + "」需要填写 true 或 false，当前填的是：" + value);
            }
        }
    }
}
