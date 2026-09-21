package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 报修请求。
 *
 * <p>报修人如果不填，服务层会用当前登录用户兜底（从 UserContext 取）。
 */
public class DeviceRepairRequest {

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 500, message = "故障描述不能超过 500 个字符")
    private String faultDesc;

    /**
     * 故障类型。存的是**字典项的值**（如 MECH），前端从「故障类型」字典的下拉里选。
     *
     * <p>选填：报修时可能还没判断出类型，不该逼着人选。
     * 值是否合法由服务层对照字典校验（字典没配就放行，见 SysDictService.isValidValue）。
     */
    @Size(max = 50, message = "故障类型不能超过 50 个字符")
    private String faultType;

    @Size(max = 50, message = "报修人不能超过 50 个字符")
    private String reporter;

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }
}
