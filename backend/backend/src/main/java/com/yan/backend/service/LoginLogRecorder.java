package com.yan.backend.service;

import com.yan.backend.common.RequestUtils;
import com.yan.backend.entity.SysLoginLog;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.SysLoginLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录日志落库。
 *
 * <p><b>为什么单独一个 bean，而且要 REQUIRES_NEW？</b>
 *
 * <p>1. <b>必须单独一个 bean</b>：Spring 的 @Transactional 基于代理，
 * 同一个类内部自调用不经过代理，事务注解不生效。这个项目里已经踩过三次了。
 *
 * <p>2. <b>必须是独立事务（REQUIRES_NEW）</b>：这是登录日志的关键点。
 * {@code AuthServiceImpl.login()} 在认证失败时会抛 AuthException，
 * 如果日志写在它的事务里，异常一抛就把日志**一起回滚**了 ——
 * 结果就是"登录成功有记录、登录失败一条都没有"。
 * 而失败记录恰恰是登录日志最有价值的部分：短时间内同一账号大量失败是撞库特征，
 * 同一 IP 换不同用户名尝试是扫描特征。把这些丢掉等于白做。
 *
 * <p>REQUIRES_NEW 会挂起外层事务、另开一个事务并独立提交，
 * 所以外层怎么回滚都影响不到日志。
 *
 * <p>另外所有写操作都吞掉异常：日志写不进去（磁盘满、表被锁）绝不能把登录本身搞挂。
 */
@Component
public class LoginLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoginLogRecorder.class);

    private final SysLoginLogRepository sysLoginLogRepository;

    public LoginLogRecorder(SysLoginLogRepository sysLoginLogRepository) {
        this.sysLoginLogRepository = sysLoginLogRepository;
    }

    /** 记录一次成功登录 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(SysUser user, String ip, String userAgent) {
        save(user.getUsername(), user.getId(), ip, userAgent, true, null);
    }

    /**
     * 记录一次失败登录。
     *
     * @param username 尝试使用的用户名（这个账号可能根本不存在）
     * @param reason   失败原因，用于页面上展示
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username, Long userId, String ip,
                              String userAgent, String reason) {
        save(username, userId, ip, userAgent, false, reason);
    }

    private void save(String username, Long userId, String ip, String userAgent,
                      boolean success, String failReason) {
        try {
            SysLoginLog entry = new SysLoginLog();
            // 用户名可能为空（请求体没填就被拦下了），给个占位免得非空约束报错
            entry.setUsername(username == null || username.isBlank() ? "(空)" : username);
            entry.setUserId(userId);
            entry.setIp(ip);
            entry.setUserAgent(userAgent);
            entry.setDevice(RequestUtils.describeDevice(userAgent));
            entry.setSuccess(success);
            entry.setFailReason(failReason);
            entry.setLoginTime(LocalDateTime.now());

            sysLoginLogRepository.save(entry);
        } catch (Exception e) {
            // 记日志失败不做任何补救，也不能往外抛 ——
            // 登录流程本身比这条记录重要得多
            log.error("写登录日志失败（不影响登录）：username={}, 原因={}", username, e.getMessage());
        }
    }
}
