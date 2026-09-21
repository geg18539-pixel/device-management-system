package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 指派维修人员 */
public class DeviceRepairAssignRequest {

    @NotBlank(message = "维修人员不能为空")
    @Size(max = 50, message = "维修人员不能超过 50 个字符")
    private String repairer;

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }
}
