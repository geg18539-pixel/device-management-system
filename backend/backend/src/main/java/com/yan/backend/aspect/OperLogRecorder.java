package com.yan.backend.aspect;

import com.yan.backend.entity.SysOperLog;
import com.yan.backend.repository.SysOperLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志落库。
 *
 * <p><b>为什么要单独抽一个类？</b>
 * 因为 @Async 是基于 Spring 代理实现的：只有在「从外部调用这个 bean 的方法」
 * 时才会走代理、切到异步线程。如果把这个方法写在 LogAspect 里、被同一个类的
 * around() 直接调用，那是类内部自调用，**不经过代理，@Async 会被静默忽略** ——
 * 方法照样跑，但是同步的，每个接口都要等日志写完才返回。
 * 这类问题不报错、只表现为"性能莫名变差"，很难查，所以拆开是必要的。
 */
@Component
public class OperLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperLogRecorder.class);

    private final SysOperLogRepository sysOperLogRepository;

    public OperLogRecorder(SysOperLogRepository sysOperLogRepository) {
        this.sysOperLogRepository = sysOperLogRepository;
    }

    @Async("logExecutor")
    public void saveAsync(SysOperLog operLog) {
        try {
            sysOperLogRepository.save(operLog);
        } catch (Exception e) {
            // 日志写失败绝不能往上抛 —— 那会把一次本来成功的业务操作变成失败。
            // 这里只记控制台，业务照常返回。
            log.error("操作日志写库失败: {}", e.getMessage(), e);
        }
    }
}
