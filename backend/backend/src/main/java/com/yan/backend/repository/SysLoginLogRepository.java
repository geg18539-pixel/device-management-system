package com.yan.backend.repository;

import com.yan.backend.entity.SysLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SysLoginLogRepository extends JpaRepository<SysLoginLog, Long>,
        JpaSpecificationExecutor<SysLoginLog> {

    Page<SysLoginLog> findAllByOrderByLoginTimeDesc(Pageable pageable);

    /** 某个时间点之后同一用户名的失败次数 —— 留给以后做"撞库告警"用 */
    long countByUsernameAndSuccessAndLoginTimeAfter(String username, Boolean success,
                                                    LocalDateTime after);
}
