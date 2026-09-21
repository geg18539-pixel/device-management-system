package com.yan.backend.repository;

import com.yan.backend.entity.DeviceRepair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface DeviceRepairRepository
        extends JpaRepository<DeviceRepair, Long>, JpaSpecificationExecutor<DeviceRepair> {

    Page<DeviceRepair> findByDeviceIdOrderByReportTimeDesc(Long deviceId, Pageable pageable);

    /**
     * 某台设备的全部工单（不分页）。
     *
     * <p>给设备详情聚合接口用：那里要拿工单 id 列表去反查消耗的配件，
     * 分页反而碍事。设备详情页展示工单时只取前若干条，截断在服务层做。
     */
    List<DeviceRepair> findByDeviceIdOrderByReportTimeDesc(Long deviceId);

    Page<DeviceRepair> findByRepairStatusOrderByReportTimeDesc(String repairStatus, Pageable pageable);

    /**
     * 按几个状态一起筛。
     *
     * <p>存在的理由：旧值「待维修」和新值「待受理」在库里同时存在，
     * 按「待受理」筛的时候要把两个值都带上（见 DeviceRepair.expandStatusFilter）。
     * 用等值匹配的版本会漏掉历史工单。
     */
    Page<DeviceRepair> findByRepairStatusInOrderByReportTimeDesc(Collection<String> statuses,
                                                                Pageable pageable);

    Page<DeviceRepair> findAllByOrderByReportTimeDesc(Pageable pageable);

    /**
     * 排除某些状态的工单。
     *
     * <p>用**排除终态**（已完成 + 已关闭）来取"未完工工单"，而不是
     * "不等于已完成" —— 后者会把已关闭的工单也算成还在处理中。
     * 以后再加中间态时，这个方法也不用改。
     */
    Page<DeviceRepair> findByRepairStatusNotInOrderByReportTimeDesc(Collection<String> statuses,
                                                                   Pageable pageable);

    /** 删除设备前检查是否有关联工单，避免留下悬空的 deviceId */
    boolean existsByDeviceId(Long deviceId);

    /**
     * 按工单状态分组统计，看板的柱状图用。
     *
     * <p>在数据库端 group by，不要把全部工单查出来在 Java 里数。
     */
    @Query("select r.repairStatus, count(r) from DeviceRepair r group by r.repairStatus")
    List<Object[]> countGroupByRepairStatus();

    /**
     * 待处理工单数 = **不在终态**的工单。
     *
     * <p>用"排除终态"而不是"等于某几个状态"，是因为以后状态流转细化之后
     * （待受理 / 已派单 / 维修中 …）新增的中间态会自动被算进来，
     * 不用每次加状态都回来改这里。
     */
    @Query("select count(r) from DeviceRepair r "
            + "where r.repairStatus <> :finished and r.repairStatus <> :closed")
    long countPending(@Param("finished") String finished, @Param("closed") String closed);

    /** 某时间点之后新建的工单数（"本月新增"用） */
    long countByReportTimeGreaterThanEqual(LocalDateTime since);

    /**
     * 按设备汇总维修情况，给**设备健康分**用。
     *
     * <p>每项是 {@code [deviceId, 累计维修次数, 近 N 天维修次数, 累计维修费用]}。
     *
     * <p>三件事都在数据库端做完：逐台设备去查它的工单会变成 N+1 查询，
     * 而健康分一次要给看板算全库设备。
     *
     * <p>⚠️ 过滤条件排除「误报作废」的单子：从「待受理」直接关闭的工单
     * 从来没被受理过（acceptTime 为空），说明那台设备其实没坏。
     * 把它计入故障次数会让一台好好的设备因为别人报错而掉分。
     * 判据用「已关闭 且 没受理过」，而不是只看状态 ——
     * 正常走完流程后关闭的工单是真实维修，必须计入。
     */
    @Query("select r.deviceId, count(r), "
            + "sum(case when r.reportTime >= :since then 1 else 0 end), "
            + "coalesce(sum(r.cost), 0) "
            + "from DeviceRepair r "
            + "where not (r.repairStatus = :closed and r.acceptTime is null) "
            + "group by r.deviceId")
    List<Object[]> aggregateForHealthScore(@Param("since") LocalDateTime since,
                                           @Param("closed") String closed);

    /**
     * 取可以当"维修案例"的工单，给故障诊断找相似案例用。
     *
     * <p>三个条件缺一不可：
     * <ul>
     *   <li>**状态在终态** —— 待受理 / 维修中的还没结论，拿它当案例是误导</li>
     *   <li>**写了维修结果** —— 只关了单但没写结果，等于没留下经验</li>
     *   <li>**在时间窗口内** —— 不设窗口的话五年前那台早就报废的设备会一直冒出来</li>
     * </ul>
     *
     * <p>用 Pageable 限制条数：打分是在内存里逐条算的，不设上限的话
     * 工单表积累几万条之后每次诊断都要遍历全表。
     */
    @Query("select r from DeviceRepair r "
            + "where r.repairStatus in :statuses "
            + "and r.repairResult is not null and r.repairResult <> '' "
            + "and r.finishTime is not null and r.finishTime >= :since "
            + "order by r.finishTime desc")
    List<DeviceRepair> findCasesSince(@Param("statuses") Collection<String> statuses,
                                      @Param("since") LocalDateTime since,
                                      Pageable pageable);

    /**
     * 取「受理时间 + 完工时间」两个时间戳，用于在服务层算平均维修时长。
     *
     * <p>为什么不在 SQL 里直接算差值：MySQL 的 TIMESTAMPDIFF 和 H2 的函数都不一样，
     * 写原生 SQL 就把测试环境和生产环境劈成了两套逻辑。
     * 只取两个时间戳、在 Java 里做减法，两边行为完全一致。
     *
     * <p>用 since 限制范围是必要的：不限的话，工单表积累几年后
     * 这个查询会越跑越慢。指标本身也只关心近期表现。
     */
    @Query("select r.acceptTime, r.finishTime from DeviceRepair r "
            + "where r.acceptTime is not null and r.finishTime is not null "
            + "and r.finishTime >= :since")
    List<Object[]> findDurationsSince(@Param("since") LocalDateTime since);

    // ============================================================
    // AI 分析结果的定向更新
    //
    // ★ 为什么不用 save(实体) 而是写这几个 update？
    //
    // AI 分析要跑十几到几十秒。如果按"先查出实体、调完模型再 save 回去"的写法，
    // 那个 save 会把**整行**（包括 repairStatus、repairer、acceptTime、cost…）
    // 用几十秒前的旧值覆盖一遍。
    // 结果就是：用户在 AI 分析期间点了「受理」或「指派」，等分析一结束，
    // 这些操作会被静默抹掉 —— 状态退回「待受理」、维修人变回空。
    // 现象非常诡异：点受理返回成功，过一会儿刷新又变回没受理。
    //
    // 定向 update 让这个异步任务**只能写它自己负责的几个字段**，
    // 从根上消除了这类丢失更新，而不是靠"窗口够短"来掩饰。
    // ============================================================

    /** 只更新「分析中」状态，不碰任何业务字段 */
    @Transactional
    @Modifying
    @Query("update DeviceRepair r set r.aiStatus = :status, r.aiError = null, "
            + "r.aiModel = :model where r.id = :id")
    int updateAiRunning(@Param("id") Long id, @Param("status") String status,
                        @Param("model") String model);

    /** 只更新分析结果字段 */
    @Transactional
    @Modifying
    @Query("update DeviceRepair r set r.aiStatus = :status, r.aiSeverity = :severity, "
            + "r.aiPossibleCauses = :causes, r.aiSuggestion = :suggestion, "
            + "r.aiEstimatedHours = :hours, r.aiModel = :model, "
            + "r.aiAnalyzedAt = :analyzedAt, r.aiError = null where r.id = :id")
    int updateAiResult(@Param("id") Long id, @Param("status") String status,
                       @Param("severity") String severity,
                       @Param("causes") String causes,
                       @Param("suggestion") String suggestion,
                       @Param("hours") BigDecimal hours,
                       @Param("model") String model,
                       @Param("analyzedAt") LocalDateTime analyzedAt);

    /** 只更新分析失败状态和原因 */
    @Transactional
    @Modifying
    @Query("update DeviceRepair r set r.aiStatus = :status, r.aiError = :error, "
            + "r.aiModel = :model where r.id = :id")
    int updateAiFailed(@Param("id") Long id, @Param("status") String status,
                       @Param("error") String error, @Param("model") String model);
}
