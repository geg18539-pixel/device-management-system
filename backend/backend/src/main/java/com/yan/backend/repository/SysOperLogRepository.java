package com.yan.backend.repository;

import com.yan.backend.entity.SysOperLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 操作日志。
 *
 * <p>只读：日志由 {@code LogAspect} 异步写入，**没有任何修改和删除接口**。
 * 审计资料能被事后改掉就失去意义了 —— 这也是等保对操作日志的基本要求。
 */
@Repository
public interface SysOperLogRepository
        extends JpaRepository<SysOperLog, Long>, JpaSpecificationExecutor<SysOperLog> {

    /**
     * 某个标题的操作日志数。
     *
     * <p>后台首页的「今日越权被拒」用它 —— 拦截器把这类拒绝写成
     * {@code title = "越权访问被拒绝"}（见 JwtInterceptor.recordDenied）。
     * 按标题匹配而不是按状态匹配：状态为「失败」的操作日志还有很多别的原因
     * （业务校验不通过、异常），混在一起数会严重虚高。
     */
    long countByTitleAndOperTimeAfter(String title, java.time.LocalDateTime since);
}
