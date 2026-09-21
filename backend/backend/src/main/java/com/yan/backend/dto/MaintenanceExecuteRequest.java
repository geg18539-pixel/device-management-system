package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 执行一次维保的请求。
 *
 * <p>执行维保会**同时做两件事**：写一条维保记录、把计划的下次到期日往后推一个周期。
 * 所以这个请求里既有"记录"的字段，也隐含了对计划的更新 ——
 * 不让前端去传 nextMaintenanceDate，那个日期由服务端按周期算，
 * 让前端算的话迟早会出现"记录写了但下次到期日没推"的不一致。
 */
public class MaintenanceExecuteRequest {

    /** 保养日期。为空按今天算 */
    private LocalDate maintenanceDate;

    @Size(max = 50, message = "保养人不能超过 50 个字符")
    private String maintainer;

    @NotBlank(message = "保养内容不能为空")
    private String content;

    /** 保养结果：正常 / 发现问题待维修 / 当场修复 */
    @Size(max = 20, message = "保养结果不能超过 20 个字符")
    private String result;

    private BigDecimal cost;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

    public LocalDate getMaintenanceDate() {
        return maintenanceDate;
    }

    public void setMaintenanceDate(LocalDate maintenanceDate) {
        this.maintenanceDate = maintenanceDate;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public void setMaintainer(String maintainer) {
        this.maintainer = maintainer;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
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
