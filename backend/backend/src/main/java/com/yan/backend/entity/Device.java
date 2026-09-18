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

import java.time.LocalDateTime;

/**
 * 设备实体，映射到 device 表。
 *
 * <p>字段名用驼峰，列名用下划线，通过每个字段上的 @Column(name = ...) 显式指定，
 * 不依赖命名策略的隐式转换，表结构一目了然。
 */
@Entity
@Table(name = "device")
public class Device {

    /** 主键，交给数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /** 设备类型 */
    @NotBlank(message = "设备类型不能为空")
    @Size(max = 50, message = "设备类型不能超过 50 个字符")
    @Column(name = "device_type", nullable = false, length = 50)
    private String deviceType;

    /** 设备序列号，全局唯一 */
    @Size(max = 100, message = "序列号不能超过 100 个字符")
    @Column(name = "serial_number", length = 100, unique = true)
    private String serialNumber;

    /** 设备状态，如：在线 / 离线 / 维修中 */
    @NotBlank(message = "设备状态不能为空")
    @Size(max = 20, message = "设备状态不能超过 20 个字符")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 所在位置 */
    @Size(max = 100, message = "位置不能超过 100 个字符")
    @Column(name = "location", length = 100)
    private String location;

    /** 备注描述 */
    @Size(max = 500, message = "描述不能超过 500 个字符")
    @Column(name = "description", length = 500)
    private String description;

    /**
     * 创建时间，由 Hibernate 在 insert 时自动填充。
     *
     * <p>updatable = false 让它不会出现在 update 语句里，保证创建时间不被改写。
     * 这里用 @Column(nullable = false) 而不用 Bean Validation 的 @NotNull：
     * 后者会被 Hibernate 的模型校验当成"实体状态不合法"，
     * 在 ddl-auto=validate 时误报 schema 校验失败。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** 更新时间，由 Hibernate 在每次 update 时自动刷新 */
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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
