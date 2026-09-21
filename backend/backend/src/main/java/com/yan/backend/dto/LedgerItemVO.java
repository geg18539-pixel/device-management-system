package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备台账的一行（一个分组，比如"一号车间"或"传感器"）。
 *
 * <p>为什么要按生命周期状态拆成四列，而不是只给一个"合计"：
 * 财务和运维看台账时的第一个问题就是"这里面有多少是能用的"。
 * 只给总数的话，还得回列表页一个个筛，台账就失去意义了。
 */
public class LedgerItemVO {

    /** 分组名（部门名 / 分类名）。归属为空时是"未分配"/"未分类" */
    private String name;

    private long total;
    private long normal;
    private long repairing;
    private long scrapped;
    private long disabled;

    /** 该分组下的设备明细。只有导出时才会填充，页面上的汇总表格不用 */
    private List<String> deviceNames = new ArrayList<>();

    public LedgerItemVO() {
    }

    public LedgerItemVO(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public List<String> getDeviceNames() {
        return deviceNames;
    }

    public void setDeviceNames(List<String> deviceNames) {
        this.deviceNames = deviceNames;
    }
}
