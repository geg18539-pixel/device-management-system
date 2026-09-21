package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 关闭工单。
 *
 * <p>关闭有两个用途，所以原因必填：
 * <ul>
 *   <li><b>作废</b>：从「待受理」直接关闭（误报、设备其实没坏、重复报修）</li>
 *   <li><b>归档</b>：从「已完成」关闭（确认没问题了，走完流程）</li>
 * </ul>
 * 不写原因的话，几个月后看到一堆"已关闭"完全想不起来为什么关的。
 */
public class DeviceRepairCloseRequest {

    @NotBlank(message = "请填写关闭原因")
    @Size(max = 200, message = "关闭原因不能超过 200 个字符")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
