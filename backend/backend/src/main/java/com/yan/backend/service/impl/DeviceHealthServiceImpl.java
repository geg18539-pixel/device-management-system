package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceHealthSummaryVO;
import com.yan.backend.dto.DeviceHealthVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SysDept;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.service.DeviceHealthService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备健康分。
 *
 * <p><b>评分口径（100 分起扣，最低 0）</b>
 * <table>
 *   <tr><td>机龄</td><td>每满 1 年 −3，最多 −15</td></tr>
 *   <tr><td>累计维修次数</td><td>每次 −5，最多 −30</td></tr>
 *   <tr><td>近 90 天维修次数</td><td>每次 −8，最多 −24</td></tr>
 *   <tr><td>累计维修费用</td><td>每 1000 元 −2，最多 −16</td></tr>
 *   <tr><td>资产状态为「维修」</td><td>−6</td></tr>
 *   <tr><td>资产状态为「停用」</td><td>−10</td></tr>
 *   <tr><td>资产状态为「报废」</td><td>直接 0 分</td></tr>
 * </table>
 *
 * <p>等级：≥90 良好、70~89 关注、&lt;70 高风险。
 *
 * <p><b>为什么机龄的扣分上限压得比较低（15）</b>：机龄是唯一"和设备自身表现无关"
 * 的因素。放大的话，一批买了六年但一直好好的设备会集体掉到"关注"档，
 * 看板上的风险清单立刻失去信息量 —— 一份把所有人都标成风险的清单，
 * 等于没有清单。年龄只该是"同样的故障次数下，老设备更值得警惕"的加权，
 * 不该单独把设备拉下水。
 *
 * <p><b>为什么近 90 天的权重比累计更高（每次 8 对 5）</b>：一台刚修的设备
 * 和一台半年没出过问题的设备，即使累计次数相同，前者明显更值得关注。
 * 近期表现才反映"现在状态如何"。
 *
 * <p><b>⚠️ 等级阈值是按实测分布定的，不是拍脑袋</b>：第一版把「良好」定在 85 分，
 * 结果用真实数据一跑，**近 90 天刚坏过一次的设备还能拿 87 分**，
 * 全库没有一台进得了风险清单 —— 功能看起来像坏的。
 * 现在改成 90 / 70，一台刚出过故障的设备会落到「关注」，
 * 故障频繁或费用高的才会进「高风险」。
 */
@Service
@Transactional(readOnly = true)
public class DeviceHealthServiceImpl implements DeviceHealthService {

    // ---------- 扣分规则 ----------
    private static final int AGE_DEDUCT_PER_YEAR = 3;
    private static final int AGE_MAX_DEDUCT = 15;
    private static final int REPAIR_DEDUCT_PER_TIME = 5;
    private static final int REPAIR_MAX_DEDUCT = 30;
    private static final int RECENT_DEDUCT_PER_TIME = 8;
    private static final int RECENT_MAX_DEDUCT = 24;
    private static final int COST_DEDUCT_PER_1000 = 2;
    private static final int COST_MAX_DEDUCT = 16;
    /** 「正在维修」是进行中的异常，比"曾经修过"更该被看到，所以给得比累计单次重 */
    private static final int STATUS_REPAIR_DEDUCT = 12;
    private static final int STATUS_DISABLED_DEDUCT = 15;

    /** "近期"的窗口。和工单页算平均维修时长用的是同一个 90 天 */
    private static final int WINDOW_DAYS = 90;

    /**
     * 低于这个分数才算"风险设备"，会被看板列出来。
     *
     * <p>等于「良好」的下界：只要不是"良好"，就该出现在待关注的清单里。
     * 两个阈值分开写的话，很容易调了一个忘了另一个，
     * 结果出现"等级显示关注、但清单里没有"的矛盾。
     */
    private static final int RISK_LIST_CUTOFF = 90;

    /** 低于这个分数算「高风险」 */
    private static final int HIGH_RISK_SCORE = 70;

    private final DeviceRepository deviceRepository;
    private final DeviceRepairRepository repairRepository;
    private final SysDeptRepository sysDeptRepository;

    public DeviceHealthServiceImpl(DeviceRepository deviceRepository,
                                   DeviceRepairRepository repairRepository,
                                   SysDeptRepository sysDeptRepository) {
        this.deviceRepository = deviceRepository;
        this.repairRepository = repairRepository;
        this.sysDeptRepository = sysDeptRepository;
    }

    // ============================================================
    // 对外
    // ============================================================

    @Override
    public DeviceHealthSummaryVO summary(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        Map<Long, RepairAggregate> aggregates = loadAllAggregates(since);
        Map<Long, String> deptNames = loadDeptNames();

        // 全库设备一次性算完评分，再切出"总数"和"前 N 条"。
        // 分别算两遍的话，两次之间数据一变就会出现
        // "卡片说 7 台有风险、列表里却列了 8 条"
        List<DeviceHealthVO> all = deviceRepository.findAll().stream()
                // 报废设备不参与排风险：它已经退役了，"风险"对一台待处理的废铁没有意义。
                // 而且它的分数恒为 0，不排掉会永远霸占清单第一名
                .filter(d -> !Device.LIFECYCLE_SCRAPPED.equals(d.getLifecycleStatus()))
                .map(d -> compute(d, aggregates.get(d.getId()), deptNames.get(d.getDeptId())))
                .filter(vo -> vo.getScore() < RISK_LIST_CUTOFF)
                .sorted(Comparator.comparingInt(DeviceHealthVO::getScore))
                .toList();

        DeviceHealthSummaryVO summary = new DeviceHealthSummaryVO();
        summary.setRiskCount(all.size());
        summary.setRiskDevices(all.stream().limit(limit).toList());
        return summary;
    }

    @Override
    public DeviceHealthVO evaluate(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在，id = " + deviceId));

        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<DeviceRepair> repairs = repairRepository.findByDeviceIdOrderByReportTimeDesc(deviceId);

        long total = 0;
        long recent = 0;
        BigDecimal cost = BigDecimal.ZERO;
        for (DeviceRepair repair : repairs) {
            // 和聚合查询保持同一口径：从「待受理」直接关闭的算误报作废，不计入
            if (DeviceRepair.STATUS_CLOSED.equals(repair.getRepairStatus())
                    && repair.getAcceptTime() == null) {
                continue;
            }
            total++;
            if (repair.getReportTime() != null && repair.getReportTime().isAfter(since)) {
                recent++;
            }
            if (repair.getCost() != null) {
                cost = cost.add(repair.getCost());
            }
        }

        String deptName = device.getDeptId() == null
                ? null : loadDeptNames().get(device.getDeptId());
        return compute(device, new RepairAggregate(total, recent, cost), deptName);
    }

    // ============================================================
    // 计算
    // ============================================================

    private DeviceHealthVO compute(Device device, RepairAggregate aggregate, String deptName) {
        RepairAggregate agg = aggregate == null ? RepairAggregate.EMPTY : aggregate;

        DeviceHealthVO vo = new DeviceHealthVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceName(device.getDeviceName());
        vo.setAssetCode(device.getAssetCode());
        vo.setDeptName(deptName == null ? "未分配" : deptName);
        vo.setLifecycleStatus(device.getLifecycleStatus());

        // 报废是终态：再怎么算都没有意义，直接 0 分收口。
        // 不特判的话，一台刚报废的新设备会因为"机龄 0、维修 0"拿到满分，
        // 出现在"健康设备"里 —— 那显然不对
        if (Device.LIFECYCLE_SCRAPPED.equals(device.getLifecycleStatus())) {
            vo.setScore(0);
            vo.setGrade(DeviceHealthVO.GRADE_RISK);
            vo.setReasons(List.of("资产已报废"));
            return vo;
        }

        int score = 100;
        // 扣分原因按"扣得多的排前面"，读的人一眼看到主要原因
        List<Deduction> deductions = new ArrayList<>();

        // ---- 机龄 ----
        if (device.getPurchaseDate() != null) {
            long years = ChronoUnit.YEARS.between(device.getPurchaseDate(), LocalDate.now());
            if (years > 0) {
                int deduct = (int) Math.min(years * AGE_DEDUCT_PER_YEAR, AGE_MAX_DEDUCT);
                score -= deduct;
                deductions.add(new Deduction("已使用 " + years + " 年", deduct));
            }
        }

        // ---- 累计维修 ----
        if (agg.total > 0) {
            int deduct = (int) Math.min(agg.total * REPAIR_DEDUCT_PER_TIME, REPAIR_MAX_DEDUCT);
            score -= deduct;
            deductions.add(new Deduction("累计维修 " + agg.total + " 次", deduct));
        }

        // ---- 近 90 天维修 ----
        if (agg.recent > 0) {
            int deduct = (int) Math.min(agg.recent * RECENT_DEDUCT_PER_TIME, RECENT_MAX_DEDUCT);
            score -= deduct;
            deductions.add(new Deduction("近 " + WINDOW_DAYS + " 天故障 " + agg.recent + " 次", deduct));
        }

        // ---- 维修费用 ----
        // 按千元取整扣分：不足 1000 元的零头不扣，避免"花了 200 块也掉分"这种噪声
        long thousands = agg.cost.divide(BigDecimal.valueOf(1000), 0, java.math.RoundingMode.DOWN)
                .longValue();
        if (thousands > 0) {
            int deduct = (int) Math.min(thousands * COST_DEDUCT_PER_1000, COST_MAX_DEDUCT);
            score -= deduct;
            deductions.add(new Deduction("累计维修费用 " + agg.cost.toPlainString() + " 元", deduct));
        }

        // ---- 当前资产状态 ----
        if (Device.LIFECYCLE_REPAIR.equals(device.getLifecycleStatus())) {
            score -= STATUS_REPAIR_DEDUCT;
            deductions.add(new Deduction("当前处于维修状态", STATUS_REPAIR_DEDUCT));
        } else if (Device.LIFECYCLE_DISABLED.equals(device.getLifecycleStatus())) {
            score -= STATUS_DISABLED_DEDUCT;
            deductions.add(new Deduction("资产已停用", STATUS_DISABLED_DEDUCT));
        }

        score = Math.max(0, Math.min(100, score));
        vo.setScore(score);
        vo.setGrade(gradeOf(score));

        deductions.sort(Comparator.comparingInt(Deduction::points).reversed());
        vo.setReasons(deductions.stream().map(Deduction::label).toList());
        return vo;
    }

    private String gradeOf(int score) {
        if (score >= RISK_LIST_CUTOFF) {
            return DeviceHealthVO.GRADE_GOOD;
        }
        if (score >= HIGH_RISK_SCORE) {
            return DeviceHealthVO.GRADE_WATCH;
        }
        return DeviceHealthVO.GRADE_RISK;
    }

    // ============================================================
    // 数据装载
    // ============================================================

    /**
     * 一次查出全库设备的维修汇总。
     *
     * <p>逐台设备去查它的工单是典型的 N+1：看板要算全库，几百台设备
     * 就是几百次查询。聚合在数据库端做完，只回来几十行。
     */
    private Map<Long, RepairAggregate> loadAllAggregates(LocalDateTime since) {
        Map<Long, RepairAggregate> map = new HashMap<>();
        for (Object[] row : repairRepository.aggregateForHealthScore(
                since, DeviceRepair.STATUS_CLOSED)) {
            Long deviceId = toLong(row[0]);
            if (deviceId == null) {
                continue;
            }
            map.put(deviceId, new RepairAggregate(
                    toLong(row[1]) == null ? 0 : toLong(row[1]),
                    toLong(row[2]) == null ? 0 : toLong(row[2]),
                    toAmount(row[3])));
        }
        return map;
    }

    private Map<Long, String> loadDeptNames() {
        Map<Long, String> map = new HashMap<>();
        for (SysDept dept : sysDeptRepository.findAll()) {
            map.put(dept.getId(), dept.getDeptName());
        }
        return map;
    }

    private Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /**
     * 费用列的兜底转换。
     *
     * <p>同一条 JPQL 在 MySQL 和 H2 上返回的类型可能不同
     * （BigDecimal / Long / Double 都遇到过），这里统一收口，
     * 免得在不同数据库上跑出 ClassCastException。
     */
    private BigDecimal toAmount(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    /** 一次设备维修情况的小结 */
    private record RepairAggregate(long total, long recent, BigDecimal cost) {
        static final RepairAggregate EMPTY = new RepairAggregate(0, 0, BigDecimal.ZERO);
    }

    /** 一条扣分项。points 只用于排序，不对外暴露 */
    private record Deduction(String label, int points) {
    }
}
