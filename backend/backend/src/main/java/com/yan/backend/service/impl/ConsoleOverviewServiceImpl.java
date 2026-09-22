package com.yan.backend.service.impl;

import com.yan.backend.dto.ConsoleOverviewVO;
import com.yan.backend.entity.DeviceKnowledge;
import com.yan.backend.entity.SysOperLog;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.KnowledgeRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.repository.SysLoginLogRepository;
import com.yan.backend.repository.SysOperLogRepository;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.ConsoleOverviewService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 后台首页的数据组装。
 *
 * <h3>为什么是九次独立的 count，而不是几条 join 起来的大查询</h3>
 *
 * <p>这几个数字来自**六张互不相关的表**（用户、角色、部门、登录日志、
 * 操作日志、知识库），硬凑成一条 SQL 只能是几个子查询拼在一起，
 * 可读性差、而且任何一处改口径都要动整条语句。
 * 分开数的话每个数字的口径都独立可见，加一个指标就是加一行。
 *
 * <p>代价是九个来回。**可以接受，因为这是"管理员一次性打开的落地页"**：
 * 没有并发压力，每个 count 都走索引；真到了性能有问题那天，
 * 再把这几个数缓存起来，接口形状不用变。
 *
 * <h3>时间范围的算法</h3>
 *
 * <p>「今天」在代码里算成 {@code LocalDate.now().atStartOfDay()} 传进查询，
 * 而不是在 SQL 里写 {@code date(x) = curdate()} —— 那种函数
 * **H2 和 MySQL 写法不同**，这个项目在方言上吃过亏（`@Lob` → tinytext 那次）。
 * 传一个时间戳进去，两边都只是普通的比较。
 */
@Service
public class ConsoleOverviewServiceImpl implements ConsoleOverviewService {

    /** 最近登录记录显示几条。多了一屏放不下，也超出"扫一眼"的用途 */
    private static final int RECENT_LOGIN_LIMIT = 5;

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysDeptRepository deptRepository;
    private final SysLoginLogRepository loginLogRepository;
    private final SysOperLogRepository operLogRepository;
    private final KnowledgeRepository knowledgeRepository;

    public ConsoleOverviewServiceImpl(SysUserRepository userRepository,
                                      SysRoleRepository roleRepository,
                                      SysDeptRepository deptRepository,
                                      SysLoginLogRepository loginLogRepository,
                                      SysOperLogRepository operLogRepository,
                                      KnowledgeRepository knowledgeRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.deptRepository = deptRepository;
        this.loginLogRepository = loginLogRepository;
        this.operLogRepository = operLogRepository;
        this.knowledgeRepository = knowledgeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ConsoleOverviewVO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        ConsoleOverviewVO vo = new ConsoleOverviewVO();

        // ---- 需要关注 ----
        vo.setLoginSuccessToday(
                loginLogRepository.countBySuccessAndLoginTimeAfter(true, todayStart));
        vo.setLoginFailToday(
                loginLogRepository.countBySuccessAndLoginTimeAfter(false, todayStart));
        // 越权被拒的判据：拦截器写的那条特定标题。**不按"状态=失败"数** ——
        // 失败的操作日志里还有业务校验不通过、异常等等，混在一起会严重虚高
        vo.setDeniedToday(operLogRepository.countByTitleAndOperTimeAfter(
                SysOperLog.TITLE_DENIED, todayStart));
        vo.setDisabledUsers(userRepository.countByStatus(SysUser.STATUS_DISABLED));
        vo.setKnowledgeFailed(
                knowledgeRepository.countByStatus(DeviceKnowledge.STATUS_FAILED));

        // ---- 规模 ----
        vo.setUserTotal(userRepository.count());
        vo.setRoleTotal(roleRepository.count());
        vo.setDeptTotal(deptRepository.count());
        vo.setKnowledgeTotal(knowledgeRepository.count());

        // ---- 最近登录 ----
        vo.setRecentLogins(loginLogRepository
                .findAllByOrderByLoginTimeDesc(PageRequest.of(0, RECENT_LOGIN_LIMIT))
                .getContent().stream()
                // success 是 Boolean 且可能为 null（老数据），
                // 用 Boolean.TRUE.equals 而不是直接拆箱 —— 拆 null 会 NPE
                .map(l -> new ConsoleOverviewVO.LoginRecord(
                        l.getUsername(),
                        l.getIp(),
                        l.getDevice(),
                        Boolean.TRUE.equals(l.getSuccess()),
                        l.getFailReason(),
                        l.getLoginTime()))
                .toList());

        return vo;
    }
}
