package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 维保记录（每次保养的存档）。
 *
 * <p>和维修工单的区别：工单是**设备坏了才报修**（被动、有故障现象），
 * 维保是**按计划定期保养**（主动、预防性）。两者都会产生费用和工时，
 * 但业务语义完全不同，所以分成两张表而不是给工单加个类型字段。
 *
 * <p>记录**只增不改不删**，属于追溯性资料。
 */
@Entity
@Table(name = "device_maintenance_record", indexes = {
        @Index(name = "idx_maintenance_record_device", columnList = "device_id,maintenance_date"),
        @Index(name = "idx_maintenance_record_plan", columnList = "plan_id")
})
public class DeviceMaintenanceRecord {

    public static final String RESULT_NORMAL = "正常";
    public static final String RESULT_NEED_REPAIR = "发现问题待维修";
    public static final String RESULT_REPAIRED = "当场修复";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的维保计划 id。允许为空 —— 也支持不挂计划的临时保养 */
    @Column(name = "plan_id")
    private Long planId;

    /** 关联设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备名称快照 */
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** 保养日期 */
    @NotNull(message = "保养日期不能为空")
    @Column(name = "maintenance_date", nullable = false)
    private LocalDate maintenanceDate;

    /** 实际执行保养的人 */
    @Size(max = 50, message = "保养人不能超过 50 个字符")
    @Column(name = "maintainer", length = 50)
    private String maintainer;

    /** 保养内容（做了什么） */
    @NotBlank(message = "保养内容不能为空")
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 保养结果：正常 / 发现问题待维修 / 当场修复 */
    @Size(max = 20, message = "保养结果不能超过 20 个字符")
    @Column(name = "result", length = 20)
    private String result;

    /** 维保费用。用 BigDecimal，金额不能用浮点数 */
    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    @Column(name = "remark", length = 500)
    private String remark;

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

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
