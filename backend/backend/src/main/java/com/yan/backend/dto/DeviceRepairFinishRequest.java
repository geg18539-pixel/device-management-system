package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 工单完工请求 */
public class DeviceRepairFinishRequest {

    @NotBlank(message = "维修人不能为空")
    @Size(max = 50, message = "维修人不能超过 50 个字符")
    private String repairer;

    /** 维修费用，选填。用 BigDecimal 接收，避免浮点误差 */
    private BigDecimal cost;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
