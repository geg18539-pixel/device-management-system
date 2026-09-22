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

    /**
     * 某个时间点之后的登录记录数。
     *
     * <p>后台首页用 {@code success=true/false} 各查一次，得到"今日登录成功/失败"。
     *
     * <p>用「某个时刻之后」而不是「今天」：SQL 里没有"今天"这个概念，
     * 而写 {@code date(login_time) = curdate()} 这类函数**在 H2 和 MySQL 上写法不同**
     * （这个项目吃过方言的亏）。传一个 {@code LocalDate.atStartOfDay()} 进来，
     * 两边都当普通的时间戳比较，不会有方言问题。
     */
    long countBySuccessAndLoginTimeAfter(Boolean success, java.time.LocalDateTime since);
}
