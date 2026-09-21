package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 工单完工请求。
 *
 * <p>报修人 / 维修人这类字段**不再强制前端传**：
 * 工单受理时可能已经指派过维修人，完工时没必要再填一遍。
 * 服务端的兜底顺序是「请求里的 → 工单上已指派的 → 当前登录用户」。
 */
public class DeviceRepairFinishRequest {

    /** 维修人。留空时用工单上已指派的，再没有就用当前登录用户 */
    @Size(max = 50, message = "维修人不能超过 50 个字符")
    private String repairer;

    /**
     * 维修结果：具体做了什么、修好没有、换了哪些件。
     *
     * <p>**必填**。这条记录是工单唯一的"处理结论"，空着的工单事后翻出来
     * 等于没有价值 —— 只看到"已完成"三个字，不知道到底干了什么。
     */
    @NotBlank(message = "请填写维修结果")
    @Size(max = 2000, message = "维修结果不能超过 2000 个字符")
    private String repairResult;

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

    public String getRepairResult() {
        return repairResult;
    }

    public void setRepairResult(String repairResult) {
        this.repairResult = repairResult;
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
