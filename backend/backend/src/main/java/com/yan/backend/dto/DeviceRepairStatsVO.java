package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 维修工单统计（工单统计看板用）。
 */
public class DeviceRepairStatsVO {

    /** 工单总数 */
    private long total;

    /** 待受理 */
    private long pending;
    /** 维修中 */
    private long repairing;
    /** 已完成 */
    private long finished;
    /** 已关闭 */
    private long closed;

    /**
     * 待处理工单数 = 不在终态里的工单（待受理 + 维修中 + 以后的中间态）。
     *
     * <p>注意它**不等于** pending 字段：pending 只是"待受理"这一种状态。
     * 页面上"待处理"要的是后者这个更大的口径。
     */
    private long pendingCount;

    /** 完成率（百分比，保留一位小数）= (已完成 + 已关闭) / 总数 */
    private double completionRate;

    /** 本月新增工单数 */
    private long thisMonthCount;

    /**
     * 近 90 天的平均维修时长（小时，保留一位小数）。
     *
     * <p>口径是「受理 → 完工」的耗时，**不是**「报修 → 完工」：
     * 后者混进了"等有人接单"的时间，那是响应速度问题，不该算进维修效率。
     * 没有足够样本时为 null，前端显示「—」而不是 0.0（0 小时会被误读成"瞬修"）。
     */
    private Double avgRepairHours;

    /** 参与平均时长计算的样本数，样本太少时提示"参考意义有限" */
    private int avgSampleSize;

    /** 状态分布，柱状图/饼图用 */
    private List<ChartItemVO> statusItems = new ArrayList<>();

    // ---------- getter / setter ----------

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPending() {
        return pending;
    }

    public void setPending(long pending) {
        this.pending = pending;
    }

    public long getRepairing() {
        return repairing;
    }

    public void setRepairing(long repairing) {
        this.repairing = repairing;
    }

    public long getFinished() {
        return finished;
    }

    public void setFinished(long finished) {
        this.finished = finished;
    }

    public long getClosed() {
        return closed;
    }

    public void setClosed(long closed) {
        this.closed = closed;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(long pendingCount) {
        this.pendingCount = pendingCount;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public long getThisMonthCount() {
        return thisMonthCount;
    }

    public void setThisMonthCount(long thisMonthCount) {
        this.thisMonthCount = thisMonthCount;
    }

    public Double getAvgRepairHours() {
        return avgRepairHours;
    }

    public void setAvgRepairHours(Double avgRepairHours) {
        this.avgRepairHours = avgRepairHours;
    }

    public int getAvgSampleSize() {
        return avgSampleSize;
    }

    public void setAvgSampleSize(int avgSampleSize) {
        this.avgSampleSize = avgSampleSize;
    }

    public List<ChartItemVO> getStatusItems() {
        return statusItems;
    }

    public void setStatusItems(List<ChartItemVO> statusItems) {
        this.statusItems = statusItems;
    }
}
