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
}
