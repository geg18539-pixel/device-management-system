package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备台账汇总（给财务 / 运维部门看的）。
 *
 * <p>三个维度：按部门、按分类、按生命周期状态。
 * 设备清单本身不在这个 VO 里 —— 台账页的明细是分页的，
 * 而导出时把明细和汇总放在同一个工作簿的不同 sheet 里。
 */
public class DeviceLedgerVO {

    /** 设备总数 */
    private long total;

    /** 四个生命周期状态各自的合计 */
    private long normal;
    private long repairing;
    private long scrapped;
    private long disabled;

    /** 按部门汇总 */
    private List<LedgerItemVO> deptItems = new ArrayList<>();

    /** 按分类汇总 */
    private List<LedgerItemVO> categoryItems = new ArrayList<>();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getNormal() {
        return normal;
    }

    public void setNormal(long normal) {
        this.normal = normal;
    }

    public long getRepairing() {
        return repairing;
    }

    public void setRepairing(long repairing) {
        this.repairing = repairing;
    }

    public long getScrapped() {
        return scrapped;
    }

    public void setScrapped(long scrapped) {
        this.scrapped = scrapped;
    }

    public long getDisabled() {
        return disabled;
    }

    public void setDisabled(long disabled) {
        this.disabled = disabled;
    }

    public List<LedgerItemVO> getDeptItems() {
        return deptItems;
    }

    public void setDeptItems(List<LedgerItemVO> deptItems) {
        this.deptItems = deptItems;
    }

    public List<LedgerItemVO> getCategoryItems() {
        return categoryItems;
    }

    public void setCategoryItems(List<LedgerItemVO> categoryItems) {
        this.categoryItems = categoryItems;
    }
}
