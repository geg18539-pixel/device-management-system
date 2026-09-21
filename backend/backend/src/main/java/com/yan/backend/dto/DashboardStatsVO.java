package com.yan.backend.dto;

import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceMaintenancePlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页数据看板的统计数据。
 *
 * <p>一次请求把指标卡、图表、待办清单全部返回，前端只发一个请求。
 * 看板是登录后第一眼看到的页面，拆成四五个接口会让首屏出现明显的"分批闪现"。
 */
public class DashboardStatsVO {

    // ---------- 指标卡 ----------

    /** 设备总数 */
    private long deviceTotal;

    /** 生命周期状态为「正常」的设备数 */
    private long lifecycleNormal;
    /** 生命周期状态为「维修」的设备数 */
    private long lifecycleRepair;
    /** 生命周期状态为「报废」的设备数 */
    private long lifecycleScrapped;
    /** 生命周期状态为「停用」的设备数 */
    private long lifecycleDisabled;

    /** 借出中的设备数 */
    private long borrowedCount;

    // ---------- 工单 ----------

    /** 工单总数 */
    private long repairTotal;
    /** 待处理工单数（不在「已完成 / 已关闭」终态里的） */
    private long repairPending;
    /** 已完成工单数 */
    private long repairFinished;
    /** 已关闭工单数 */
    private long repairClosed;
    /** 工单完成率（百分比，保留一位小数）。已完成+已关闭 占全部的比重 */
    private double repairCompletionRate;

    // ---------- 维保提醒 ----------

    /** 即将到期 / 已过保的设备数量（不含已报废）。这里指**厂商保修期** */
    private long warrantyExpiringCount;
    /** 提前多少天算「即将到期」，由后端配置，前端只负责显示 */
    private int warrantyWarnDays;

    /**
     * 即将到期 / 已逾期的**维保计划**数量（不含已报废设备）。
     *
     * <p>注意和上面的 warrantyExpiringCount 是两件事：
     * 那个是"厂商保修期快到了"（过了保修，维修要自费），
     * 这个是"该安排保养了"（预防性维护计划到期）。
     * 企业设备管理里两个都要盯，混在一起会漏掉其中一类。
     */
    private long maintenanceDueCount;
    /** 维保到期预警窗口天数 */
    private int maintenanceWarnDays;

    /** 库存告急的配件数量（库存 ≤ 预警阈值） */
    private long lowStockCount;

    /** 健康分偏低（<85）的设备台数。看板的健康风险卡用 */
    private long healthRiskCount;

    // ---------- 图表 ----------

    /** 设备生命周期分布，饼图用 */
    private List<ChartItemVO> lifecycleItems = new ArrayList<>();
    /** 工单状态分布，柱状图用 */
    private List<ChartItemVO> repairStatusItems = new ArrayList<>();
    /** 各部门设备数量，柱状图用 */
    private List<ChartItemVO> deptDeviceItems = new ArrayList<>();

    // ---------- 清单 ----------

    /** 即将到期 / 已过保的设备（最多若干条，按到期日从近到远） */
    private List<Device> warrantyExpiringDevices = new ArrayList<>();

    /** 即将到期 / 已逾期的维保计划（最多若干条，按到期日从近到远） */
    private List<DeviceMaintenancePlan> maintenanceDuePlans = new ArrayList<>();

    /** 健康分最低的若干台设备，按分数升序。看板的健康风险卡用 */
    private List<DeviceHealthVO> healthRiskDevices = new ArrayList<>();

    // ---------- getter / setter ----------

    public long getDeviceTotal() {
        return deviceTotal;
    }

    public void setDeviceTotal(long deviceTotal) {
        this.deviceTotal = deviceTotal;
    }

    public long getLifecycleNormal() {
        return lifecycleNormal;
    }

    public void setLifecycleNormal(long lifecycleNormal) {
        this.lifecycleNormal = lifecycleNormal;
    }

    public long getLifecycleRepair() {
        return lifecycleRepair;
    }

    public void setLifecycleRepair(long lifecycleRepair) {
        this.lifecycleRepair = lifecycleRepair;
    }

    public long getLifecycleScrapped() {
        return lifecycleScrapped;
    }

    public void setLifecycleScrapped(long lifecycleScrapped) {
        this.lifecycleScrapped = lifecycleScrapped;
    }

    public long getLifecycleDisabled() {
        return lifecycleDisabled;
    }

    public void setLifecycleDisabled(long lifecycleDisabled) {
        this.lifecycleDisabled = lifecycleDisabled;
    }

    public long getBorrowedCount() {
        return borrowedCount;
    }

    public void setBorrowedCount(long borrowedCount) {
        this.borrowedCount = borrowedCount;
    }

    public long getRepairTotal() {
        return repairTotal;
    }

    public void setRepairTotal(long repairTotal) {
        this.repairTotal = repairTotal;
    }

    public long getRepairPending() {
        return repairPending;
    }

    public void setRepairPending(long repairPending) {
        this.repairPending = repairPending;
    }

    public long getRepairFinished() {
        return repairFinished;
    }

    public void setRepairFinished(long repairFinished) {
        this.repairFinished = repairFinished;
    }

    public long getRepairClosed() {
        return repairClosed;
    }

    public void setRepairClosed(long repairClosed) {
        this.repairClosed = repairClosed;
    }

    public double getRepairCompletionRate() {
        return repairCompletionRate;
    }

    public void setRepairCompletionRate(double repairCompletionRate) {
        this.repairCompletionRate = repairCompletionRate;
    }

    public long getWarrantyExpiringCount() {
        return warrantyExpiringCount;
    }

    public void setWarrantyExpiringCount(long warrantyExpiringCount) {
        this.warrantyExpiringCount = warrantyExpiringCount;
    }

    public int getWarrantyWarnDays() {
        return warrantyWarnDays;
    }

    public void setWarrantyWarnDays(int warrantyWarnDays) {
        this.warrantyWarnDays = warrantyWarnDays;
    }

    public List<ChartItemVO> getLifecycleItems() {
        return lifecycleItems;
    }

    public void setLifecycleItems(List<ChartItemVO> lifecycleItems) {
        this.lifecycleItems = lifecycleItems;
    }

    public List<ChartItemVO> getRepairStatusItems() {
        return repairStatusItems;
    }

    public void setRepairStatusItems(List<ChartItemVO> repairStatusItems) {
        this.repairStatusItems = repairStatusItems;
    }

    public List<ChartItemVO> getDeptDeviceItems() {
        return deptDeviceItems;
    }

    public void setDeptDeviceItems(List<ChartItemVO> deptDeviceItems) {
        this.deptDeviceItems = deptDeviceItems;
    }

    public List<Device> getWarrantyExpiringDevices() {
        return warrantyExpiringDevices;
    }

    public void setWarrantyExpiringDevices(List<Device> warrantyExpiringDevices) {
        this.warrantyExpiringDevices = warrantyExpiringDevices;
    }

    public long getMaintenanceDueCount() {
        return maintenanceDueCount;
    }

    public void setMaintenanceDueCount(long maintenanceDueCount) {
        this.maintenanceDueCount = maintenanceDueCount;
    }

    public int getMaintenanceWarnDays() {
        return maintenanceWarnDays;
    }

    public void setMaintenanceWarnDays(int maintenanceWarnDays) {
        this.maintenanceWarnDays = maintenanceWarnDays;
    }

    public long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public List<DeviceMaintenancePlan> getMaintenanceDuePlans() {
        return maintenanceDuePlans;
    }

    public void setMaintenanceDuePlans(List<DeviceMaintenancePlan> maintenanceDuePlans) {
        this.maintenanceDuePlans = maintenanceDuePlans;
    }

    public long getHealthRiskCount() {
        return healthRiskCount;
    }

    public void setHealthRiskCount(long healthRiskCount) {
        this.healthRiskCount = healthRiskCount;
    }

    public List<DeviceHealthVO> getHealthRiskDevices() {
        return healthRiskDevices;
    }

    public void setHealthRiskDevices(List<DeviceHealthVO> healthRiskDevices) {
        this.healthRiskDevices = healthRiskDevices;
    }
}
