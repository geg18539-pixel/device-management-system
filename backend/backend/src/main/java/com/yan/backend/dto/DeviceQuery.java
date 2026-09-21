package com.yan.backend.dto;

/**
 * 设备列表的查询条件。
 *
 * <p>抽成对象而不是一串方法参数：筛选维度已经有 7 个了，
 * 再往下加就会变成
 * {@code page(1, 10, 2L, 3L, "在线", "正常", 30, "阀门")}
 * 这种调用方必然写错的签名（哪个 Long 是分类、哪个是部门靠猜）。
 *
 * <p>Spring MVC 会自动把 URL 上的同名 query 参数绑到这个对象的字段上，
 * 所以前端 {@code /api/devices/page?deptId=3&pageNum=1} 这样的调用**不用改**。
 */
public class DeviceQuery {

    /** 页码，从 1 开始 */
    private int pageNum = 1;

    private int pageSize = 10;

    /** 按分类筛选 */
    private Long categoryId;

    /** 按归属部门筛选 */
    private Long deptId;

    /** 按**连通状态**筛选：在线 / 离线 / 维修中 / 使用中 */
    private String status;

    /**
     * 按**生命周期状态**筛选：正常 / 维修 / 报废 / 停用。
     *
     * <p>和上面的 status 是两个维度，别混。用户说的"按设备状态筛选"
     * 通常指的是这个（资产状态）。
     */
    private String lifecycleStatus;

    /**
     * 保修到期筛选：只看"到期日在 N 天以内"的设备（含已经过保的）。
     *
     * <p>用天数而不是日期区间，是因为这个筛选的实际用途就是
     * "看看哪些快过保了"，选 30 / 60 / 90 天远比让用户自己填两个日期方便。
     */
    private Integer warrantyWithinDays;

    /** 关键词：设备名称 / 资产编号 / 序列号 */
    private String keyword;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Integer getWarrantyWithinDays() {
        return warrantyWithinDays;
    }

    public void setWarrantyWithinDays(Integer warrantyWithinDays) {
        this.warrantyWithinDays = warrantyWithinDays;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
