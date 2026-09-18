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

    @Size(max = 50, message = "报修人不能超过 50 个字符")
    private String reporter;

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }
}
