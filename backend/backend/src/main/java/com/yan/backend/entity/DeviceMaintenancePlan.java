package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备维保计划（定期保养任务）。
 *
 * <p>一台设备一条计划（同一台设备不需要多套周期），记录"多久保养一次、
 * 上次是什么时候、下次该什么时候"。
 *
 * <p>核心字段是 {@link #nextMaintenanceDate}：页面的到期告警和首页看板的
 * "维保即将到期"都只看这一个日期。它由 {@code lastMaintenanceDate + cycleDays}
 * 算出，每次执行维保后自动往后推一个周期，不需要人工维护。
 */
@Entity
@Table(name = "device_maintenance_plan", indexes = {
        // 到期告警查的是"next_maintenance_date <= 某天"，加索引
        @Index(name = "idx_plan_next_date", columnList = "next_maintenance_date"),
        @Index(name = "idx_plan_device", columnList = "device_id")
})
public class DeviceMaintenancePlan {

    public static final String STATUS_ENABLED = "启用";
    public static final String STATUS_DISABLED = "停用";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /** 设备名称快照，列表页展示不用再 join 设备表 */
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** 计划名称，如"季度保养"、"年度大修" */
    @NotBlank(message = "计划名称不能为空")
    @Size(max = 100, message = "计划名称不能超过 100 个字符")
    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    /**
     * 保养周期（天）。执行一次维保后，下次到期日按这个天数往后推。
     *
     * <p>用天数而不是"每月/每季"这种枚举：天数能表达任意周期，
     * 而且计算下次日期只是一次加法，不用处理"每月 31 号"这类边界问题。
     */
    @NotNull(message = "保养周期不能为空")
    @Min(value = 1, message = "保养周期至少 1 天")
    @Column(name = "cycle_days", nullable = false)
    private Integer cycleDays;

    /** 上次保养日期。null 表示还没保养过 */
    @Column(name = "last_maintenance_date")
    private LocalDate lastMaintenanceDate;

    /** 下次到期日 = 上次保养日 + 周期天数。告警只看这个字段 */
    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    /** 负责人（企业里通常是设备管理员，不是每次实际动手的维修工） */
    @Size(max = 50, message = "负责人不能超过 50 个字符")
    @Column(name = "maintainer", length = 50)
    private String maintainer;

    /** 启用 / 停用。停用的计划不参与到期告警 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ENABLED;

    @Size(max = 200, message = "备注不能超过 200 个字符")
    @Column(name = "remark", length = 200)
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

    public String getPlanName() {
        return planName;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public Integer getCycleDays() {
        return cycleDays;
    }

    public void setCycleDays(Integer cycleDays) {
        this.cycleDays = cycleDays;
    }

    public LocalDate getLastMaintenanceDate() {
        return lastMaintenanceDate;
    }

    public void setLastMaintenanceDate(LocalDate lastMaintenanceDate) {
        this.lastMaintenanceDate = lastMaintenanceDate;
    }

    public LocalDate getNextMaintenanceDate() {
        return nextMaintenanceDate;
    }

    public void setNextMaintenanceDate(LocalDate nextMaintenanceDate) {
        this.nextMaintenanceDate = nextMaintenanceDate;
    }

    public String getMaintainer() {
        return maintainer;
    }

    public void setMaintainer(String maintainer) {
        this.maintainer = maintainer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
