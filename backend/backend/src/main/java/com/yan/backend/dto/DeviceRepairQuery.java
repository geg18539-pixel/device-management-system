package com.yan.backend.dto;

/**
 * 工单列表的查询条件。
 *
 * <p>和 {@link DeviceQuery} 一样收成对象：维度多了以后平铺成方法参数，
 * 调用方分不清哪个 String 是状态、哪个是关键词。
 */
public class DeviceRepairQuery {

    private int pageNum = 1;
    private int pageSize = 10;

    /** 按状态筛选。可以是「待受理」「维修中」「已完成」「已关闭」，也可以是旧值「待维修」 */
    private String repairStatus;

    /** 按设备筛选 */
    private Long deviceId;

    /** 按维修人员筛选 */
    private String repairer;

    /**
     * 按故障类型筛选。
     *
     * <p>传的是**字典项的值**（如 MECH），不是展示文案 ——
     * 字典的文案可能被改过，按文案筛会漏掉历史工单。
     */
    private String faultType;

    /** 关键词：设备名 / 故障描述 / 报修人 */
    private String keyword;

    /**
     * 只看待处理（不在终态里的工单）。
     *
     * <p>单独一个开关而不用 repairStatus 表达：待处理**跨多个状态**
     * （待受理 + 维修中，以后还可能加别的中间态），
     * 而 repairStatus 是等值匹配。用状态传的话，每加一个中间态，
     * 前端这个筛选就得跟着改。
     */
    private Boolean pendingOnly;

    // ---------- getter / setter ----------

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(String repairStatus) {
        this.repairStatus = repairStatus;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getPendingOnly() {
        return pendingOnly;
    }

    public void setPendingOnly(Boolean pendingOnly) {
        this.pendingOnly = pendingOnly;
    }
}
