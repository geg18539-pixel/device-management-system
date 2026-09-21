package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 一次故障诊断的请求 */
public class DiagnosisRequest {

    @NotBlank(message = "故障描述不能为空")
    @Size(max = 1000, message = "故障描述不能超过 1000 个字符")
    private String faultDesc;

    /** 选填。指定了的话会带上这台设备的型号和它的历史工单 */
    private Long deviceId;

    /**
     * 选填。故障类型（字典值，如 MECH）。
     *
     * <p>从报修表单过来时会带上，用它给"同类故障"的历史工单加分 ——
     * 同一类故障的处理经验，比文字碰巧重合的更值得参考。
     */
    private String faultType;

    /** 选填。不传用配置里的默认对话模型 */
    private String model;

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
