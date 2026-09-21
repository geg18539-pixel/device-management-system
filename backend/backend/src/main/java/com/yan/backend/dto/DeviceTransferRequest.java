package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 设备调拨请求。
 *
 * <p>{@code toDeptId} 允许为 null，表示调到"未分配"——
 * 设备从某个部门收回、还没确定新归属时是常见操作，不该逼用户随便选一个。
 */
public class DeviceTransferRequest {

    /** 目标部门 id。null 表示调到未分配 */
    private Long toDeptId;

    @NotBlank(message = "调拨原因不能为空")
    @Size(max = 200, message = "调拨原因不能超过 200 个字符")
    private String reason;

    public Long getToDeptId() {
        return toDeptId;
    }

    public void setToDeptId(Long toDeptId) {
        this.toDeptId = toDeptId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
