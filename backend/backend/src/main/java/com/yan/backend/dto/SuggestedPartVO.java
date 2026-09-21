package com.yan.backend.dto;

/**
 * 建议备件。
 *
 * <p>来源是**相似历史工单实际领用过**的配件，不是让模型凭空猜。
 * 每一条都带"在几条相似工单里用过"，所以运维能自己判断这条建议有多可信。
 *
 * <p>比"让大模型直接说该换什么件"可靠得多：模型不知道你库里有什么件、
 * 哪个还有库存，而这里给的是真实发生过的领用记录。
 */
public class SuggestedPartVO {

    private Long partId;
    private String partCode;
    private String partName;
    private String unit;

    /** 当前库存。库存为 0 时前端要标出来 —— 建议你换一个库里没有的件是没用的 */
    private Integer stockQuantity;

    /** 在几条相似工单里被领用过 */
    private int usageCount;

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
    }

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }
}
