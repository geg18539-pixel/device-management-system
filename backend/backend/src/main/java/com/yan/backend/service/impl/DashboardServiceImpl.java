package com.yan.backend.service.impl;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.dto.ChartItemVO;
import com.yan.backend.dto.DashboardStatsVO;
import com.yan.backend.dto.DeviceHealthSummaryVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SysDept;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.service.DashboardService;
import com.yan.backend.service.DeviceHealthService;
import com.yan.backend.service.DeviceMaintenanceService;
import com.yan.backend.service.SparePartService;
import com.yan.backend.service.SysConfigService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页看板统计。
 *
 * <p>所有聚合都在数据库端完成（group by / count），
 * 不把整表查出来在 Java 里遍历 —— 设备量一大那就是全表扫描加内存聚合。
 */
@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    /** 即将到期清单最多返回多少条。看板那一块是个表格，不是报表，塞太多没人看 */
    private static final int WARRANTY_LIST_LIMIT = 10;

    /** 维保到期清单最多返回多少条 */
    private static final int MAINTENANCE_LIST_LIMIT = 10;

    /** 健康风险清单最多返回多少条。风险数可能远多于这里，所以卡片上同时给总数 */
    private static final int HEALTH_RISK_LIST_LIMIT = 5;

    private final DeviceRepository deviceRepository;
    private final DeviceRepairRepository deviceRepairRepository;
    private final SysDeptRepository sysDeptRepository;
    private final DeviceMaintenanceService maintenanceService;
    private final SparePartService sparePartService;
    private final SysConfigService configService;
    private final DeviceHealthService deviceHealthService;

    public DashboardServiceImpl(DeviceRepository deviceRepository,
                                DeviceRepairRepository deviceRepairRepository,
                                SysDeptRepository sysDeptRepository,
                                DeviceMaintenanceService maintenanceService,
                                SparePartService sparePartService,
                                SysConfigService configService,
                                DeviceHealthService deviceHealthService) {
        this.deviceRepository = deviceRepository;
        this.deviceRepairRepository = deviceRepairRepository;
        this.sysDeptRepository = sysDeptRepository;
        this.maintenanceService = maintenanceService;
        this.sparePartService = sparePartService;
        this.configService = configService;
        this.deviceHealthService = deviceHealthService;
    }

    /**
     * 维保到期预警窗口天数。
     *
     * <p>从**系统参数**读，和维保页面共用同一个配置项 ——
     * 两处各读各的常量迟早会不一致（页面上按 30 天报、
     * 看板按 90 天报，用户会以为其中一个是错的）。
     */
    private int maintenanceWarnDays() {
        return configService.getInt(
                ConfigKeys.MAINTENANCE_WARN_DAYS, ConfigKeys.MAINTENANCE_WARN_DAYS_DEFAULT);
    }

    /** 保修到期预警窗口天数。原来硬编码成常量，现在也放出来可配 */
    private int warrantyWarnDays() {
        return configService.getInt(
                ConfigKeys.WARRANTY_WARN_DAYS, ConfigKeys.WARRANTY_WARN_DAYS_DEFAULT);
    }

    @Override
    public DashboardStatsVO stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setWarrantyWarnDays(warrantyWarnDays());
        vo.setMaintenanceWarnDays(maintenanceWarnDays());

        buildDeviceStats(vo);
        buildRepairStats(vo);
        buildDeptStats(vo);
        buildWarrantyStats(vo);
        buildMaintenanceStats(vo);
        buildSparePartStats(vo);
        buildHealthStats(vo);

        return vo;
    }

    // ---------------- 设备健康 ----------------

    /**
     * 健康风险设备。
     *
     * <p>分值是**算出来的**，不是存的 —— 每次请求现算，永远和当前的
     * 维修记录、资产状态一致。这也是不建 device_health_score 表的原因：
     * 落表就要回答"什么时候更新"，而漏更新不会报错，只会让看板显示一个
     * 和事实不符的分数。
     */
    private void buildHealthStats(DashboardStatsVO vo) {
        DeviceHealthSummaryVO summary = deviceHealthService.summary(HEALTH_RISK_LIST_LIMIT);
        vo.setHealthRiskCount(summary.getRiskCount());
        vo.setHealthRiskDevices(summary.getRiskDevices());
    }

    // ---------------- 设备 ----------------

    private void buildDeviceStats(DashboardStatsVO vo) {
        vo.setDeviceTotal(deviceRepository.count());
        vo.setBorrowedCount(deviceRepository.countByBorrowerIsNotNull());

        // group by 的结果里，生命周期状态可能是 null（老数据，虽然启动时会回填，
        // 但万一回填失败/刚加列还没回填，这里也不能把 null 丢进图表里当一类）。
        // 所以按"正常"归并，并且**合并计数**而不是覆盖。
        List<ChartItemVO> items = new ArrayList<>();
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Object[] row : deviceRepository.countGroupByLifecycle()) {
            String name = (row[0] == null || String.valueOf(row[0]).isBlank())
                    ? Device.LIFECYCLE_NORMAL
                    : String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            merged.merge(name, count, Long::sum);
        }
        for (Map.Entry<String, Long> entry : merged.entrySet()) {
            items.add(new ChartItemVO(entry.getKey(), entry.getValue()));
        }
        vo.setLifecycleItems(items);

        // 指标卡上的四个数字直接取自上面这份合并结果，
        // 保证卡片和饼图永远对得上（分别单独查一次就可能出现对不上的情况）
        vo.setLifecycleNormal(merged.getOrDefault(Device.LIFECYCLE_NORMAL, 0L));
        vo.setLifecycleRepair(merged.getOrDefault(Device.LIFECYCLE_REPAIR, 0L));
        vo.setLifecycleScrapped(merged.getOrDefault(Device.LIFECYCLE_SCRAPPED, 0L));
        vo.setLifecycleDisabled(merged.getOrDefault(Device.LIFECYCLE_DISABLED, 0L));
    }

    // ---------------- 工单 ----------------

    private void buildRepairStats(DashboardStatsVO vo) {
        vo.setRepairTotal(deviceRepairRepository.count());

        vo.setRepairPending(deviceRepairRepository.countPending(
                DeviceRepair.STATUS_FINISHED, DeviceRepair.STATUS_CLOSED));

        List<ChartItemVO> items = new ArrayList<>();
        long finished = 0;
        long closed = 0;
        for (Object[] row : deviceRepairRepository.countGroupByRepairStatus()) {
            String name = row[0] == null ? "未知" : String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            items.add(new ChartItemVO(name, count));
            if (DeviceRepair.STATUS_FINISHED.equals(name)) {
                finished = count;
            } else if (DeviceRepair.STATUS_CLOSED.equals(name)) {
                closed = count;
            }
        }
        vo.setRepairStatusItems(items);
        vo.setRepairFinished(finished);
        vo.setRepairClosed(closed);

        // 完成率 = 终态工单 / 全部工单。
        // 分母为 0 时直接给 0 而不是 NaN —— 前端拿 NaN 会渲染成 "NaN%"。
        long total = vo.getRepairTotal();
        vo.setRepairCompletionRate(total == 0
                ? 0.0
                : Math.round((finished + closed) * 1000.0 / total) / 10.0);
    }

    // ---------------- 部门分布 ----------------

    private void buildDeptStats(DashboardStatsVO vo) {
        // 部门数量很少，一次查全表在内存里做 id → 名称的映射即可，不用 join
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (SysDept dept : sysDeptRepository.findAllByOrderBySortOrderAscIdAsc()) {
            deptNames.put(dept.getId(), dept.getDeptName());
        }

        List<ChartItemVO> items = new ArrayList<>();
        for (Object[] row : deviceRepository.countGroupByDept()) {
            Long deptId = row[0] == null ? null : ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            String name = deptId == null
                    ? "未分配"
                    : deptNames.getOrDefault(deptId, "部门#" + deptId);
            items.add(new ChartItemVO(name, count));
        }
        vo.setDeptDeviceItems(items);
    }

    // ---------------- 维保到期提醒 ----------------

    private void buildWarrantyStats(DashboardStatsVO vo) {
        LocalDate deadline = LocalDate.now().plusDays(warrantyWarnDays());

        // 清单只取前 N 条给页面展示
        List<Device> devices = deviceRepository.findWarrantyExpiring(
                deadline, Device.LIFECYCLE_SCRAPPED,
                PageRequest.of(0, WARRANTY_LIST_LIMIT));
        vo.setWarrantyExpiringDevices(devices);

        // 数量要的是**总数**而不是清单长度 —— 页面提示写"共 3 台即将到期"，
        // 如果直接拿清单长度，超过 10 台时会显示成 10，是错的。
        vo.setWarrantyExpiringCount(deviceRepository.countWarrantyExpiring(
                deadline, Device.LIFECYCLE_SCRAPPED));
    }

    // ---------------- 维保到期提醒 ----------------

    private void buildMaintenanceStats(DashboardStatsVO vo) {
        // 直接复用维保 Service 的口径（它会排除已报废设备）。
        // 看板这里再写一份过滤条件的话，两处迟早会因为改动而对不上
        List<DeviceMaintenancePlan> due = maintenanceService.listDue(maintenanceWarnDays());

        vo.setMaintenanceDueCount(due.size());
        vo.setMaintenanceDuePlans(due.size() > MAINTENANCE_LIST_LIMIT
                ? due.subList(0, MAINTENANCE_LIST_LIMIT)
                : due);
    }

    // ---------------- 配件库存预警 ----------------

    private void buildSparePartStats(DashboardStatsVO vo) {
        vo.setLowStockCount(sparePartService.countLowStock());
    }
}
