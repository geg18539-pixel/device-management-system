package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 设备调拨记录（一次调拨一行）。
 *
 * <p><b>为什么单独一张表而不是只改 Device.deptId？</b>
 * 一台设备在企业里会被调来调去，而"它现在在哪个部门"和"它被谁在什么时候
 * 从哪调到哪"是两个不同的问题。只改 deptId 的话，调拨历史完全丢失，
 * 出了资产纠纷就无从查证。所以每次调拨都追加一条记录，Device.deptId 只是
 * 这条流水的最新结果。
 *
 * <p>记录**只增不改不删**，属于追溯性资料。
 *
 * <p>部门名存的是**快照**（fromDeptName / toDeptName）而不是只存 id：
 * 部门以后可能改名甚至被删，历史记录应该反映"当时叫什么"，
 * 否则几年前的调拨记录会显示成今天的部门名，或者直接显示成"部门#7"。
 */
@Entity
@Table(name = "device_transfer", indexes = {
        @Index(name = "idx_transfer_device", columnList = "device_id,transfer_time")
})
public class DeviceTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被调拨的设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 调拨时设备名称的快照 */
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** 原部门 id。null 表示调拨前未分配部门 */
    @Column(name = "from_dept_id")
    private Long fromDeptId;

    @Size(max = 50, message = "部门名称不能超过 50 个字符")
    @Column(name = "from_dept_name", length = 50)
    private String fromDeptName;

    /** 目标部门 id。null 表示调到"未分配" */
    @Column(name = "to_dept_id")
    private Long toDeptId;

    @Size(max = 50, message = "部门名称不能超过 50 个字符")
    @Column(name = "to_dept_name", length = 50)
    private String toDeptName;

    @NotBlank(message = "调拨原因不能为空")
    @Size(max = 200, message = "调拨原因不能超过 200 个字符")
    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Size(max = 50, message = "操作人不能超过 50 个字符")
    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "transfer_time", nullable = false)
    private LocalDateTime transferTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Long getFromDeptId() {
        return fromDeptId;
    }

    public void setFromDeptId(Long fromDeptId) {
        this.fromDeptId = fromDeptId;
    }

    public String getFromDeptName() {
        return fromDeptName;
    }

    public void setFromDeptName(String fromDeptName) {
        this.fromDeptName = fromDeptName;
    }

    public Long getToDeptId() {
        return toDeptId;
    }

    public void setToDeptId(Long toDeptId) {
        this.toDeptId = toDeptId;
    }

    public String getToDeptName() {
        return toDeptName;
    }

    public void setToDeptName(String toDeptName) {
        this.toDeptName = toDeptName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getTransferTime() {
        return transferTime;
    }

    public void setTransferTime(LocalDateTime transferTime) {
        this.transferTime = transferTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
