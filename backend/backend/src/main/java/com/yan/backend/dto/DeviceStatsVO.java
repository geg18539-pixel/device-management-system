package com.yan.backend.dto;

import java.util.List;

/**
 * 设备统计。一次请求把两个图表要的数据都返回，前端不用发两次。
 */
public class DeviceStatsVO {

    /** 设备状态分布，给饼图用 */
    private List<ChartItemVO> statusItems;

    /** 设备分类统计，给柱状图用 */
    private List<ChartItemVO> categoryItems;

    /** 设备总数 */
    private long total;

    public List<ChartItemVO> getStatusItems() {
        return statusItems;
    }

    public void setStatusItems(List<ChartItemVO> statusItems) {
        this.statusItems = statusItems;
    }

    public List<ChartItemVO> getCategoryItems() {
        return categoryItems;
    }

    public void setCategoryItems(List<ChartItemVO> categoryItems) {
        this.categoryItems = categoryItems;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
