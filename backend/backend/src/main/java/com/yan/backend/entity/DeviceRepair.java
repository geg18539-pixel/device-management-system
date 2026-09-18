package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备维修工单。
 *
 * <p>一张工单对应一次报修到完工的过程。设备报修时创建工单（状态"待维修"），
 * 同时把设备状态改成"维修中"；工单完工时把设备状态改回"在线"。
 * 这个联动放在 Service 里做，保证两边状态一致。
 */
@Entity
@Table(name = "device_repair")
public class DeviceRepair {

    // ---------- 工单状态常量 ----------
    public static final String STATUS_PENDING = "待维修";
    public static final String STATUS_REPAIRING = "维修中";
    public static final String STATUS_FINISHED = "已完成";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 报修时设备的名称**快照**。
     *
     * <p>有意做冗余：工单是历史记录，应该反映"当时"设备叫什么。
     * 设备后来改名（甚至删除）不应该影响历史工单的可读性，
     * 而且列表页展示时也不用再 join 一次设备表。
     */
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 500, message = "故障描述不能超过 500 个字符")
    @Column(name = "fault_desc", nullable = false, length = 500)
    private String faultDesc;

    /** 工单状态：待维修 / 维修中 / 已完成 */
    @Column(name = "repair_status", nullable = false, length = 20)
    private String repairStatus = STATUS_PENDING;

    /** 报修人 */
    @Size(max = 50, message = "报修人不能超过 50 个字符")
    @Column(name = "reporter", length = 50)
    private String reporter;

    /** 维修人，完工时填写 */
    @Size(max = 50, message = "维修人不能超过 50 个字符")
    @Column(name = "repairer", length = 50)
    private String repairer;

    /** 报修时间 */
    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    /** 完工时间 */
    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    /** 维修费用。用 BigDecimal 而不是 double —— 金额计算不能用浮点数 */
    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

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

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(String repairStatus) {
        this.repairStatus = repairStatus;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
