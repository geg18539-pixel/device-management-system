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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备实体，映射到 device 表。
 *
 * <p>字段名用驼峰，列名用下划线，通过每个字段上的 @Column(name = ...) 显式指定，
 * 不依赖命名策略的隐式转换，表结构一目了然。
 *
 * <p>5.5 为了做"资产全生命周期"扩充了资产属性（资产编号、采购日期、保修到期日、
 * 分类）和当前借用信息。**新增的列全部允许为空** —— 表里已经有数据，
 * 加 NOT NULL 列会让已有行的插入/校验失败。
 */
@Entity
@Table(name = "device")
public class Device {

    // ---------- 设备状态常量 ----------
    // status 是自由字符串（历史原因），这些常量避免各处硬编码写错
    public static final String STATUS_ONLINE = "在线";
    public static final String STATUS_OFFLINE = "离线";
    public static final String STATUS_REPAIRING = "维修中";
    public static final String STATUS_IN_USE = "使用中";

    // ---------- 资产生命周期状态常量 ----------
    // ★ 和上面的 status 是**两个不同维度**，不要混用：
    //   status          反映"连通性" —— 设备当前能不能通信（在线/离线/使用中/维修中）
    //   lifecycleStatus 反映"资产状态" —— 这台设备在账上处于生命周期的哪个阶段
    // 一台设备可以「在线且已报废」（信号还在，但资产上要淘汰），
    // 也可以「离线但正常」（只是网断了）。所以必须是两个字段。
    public static final String LIFECYCLE_NORMAL = "正常";
    public static final String LIFECYCLE_REPAIR = "维修";
    public static final String LIFECYCLE_SCRAPPED = "报废";
    public static final String LIFECYCLE_DISABLED = "停用";

    /** 主键，交给数据库自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 设备名称 */
    @NotBlank(message = "设备名称不能为空")
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    /**
     * 设备类型（自由文本）。
     *
     * <p>5.5 引入了结构化的 DeviceCategory 之后，这个字段降级为**遗留字段**：
     * 去掉了 @NotBlank（新表单不再要求填），已有数据保持不动，避免破坏历史记录。
     * 新代码请用 categoryId。
     */
    @Size(max = 50, message = "设备类型不能超过 50 个字符")
    @Column(name = "device_type", length = 50)
    private String deviceType;

    /**
     * 所属分类 id，指向 device_category 表。null 表示未分类。
     *
     * <p>这里存的是裸 id 而不是 @ManyToOne 关联。原因是项目开了
     * open-in-view=false，用关联对象容易在序列化时踩 LazyInitializationException
     * （5.3 已经在 SysRole.menus 上踩过一次）。裸 id 配合前端已有的分类树做映射，
     * 既没有懒加载风险，也不会产生 N+1 查询。
     */
    @Column(name = "category_id")
    private Long categoryId;

    /** 资产编号，如 ZC-2026-0001 */
    @Size(max = 50, message = "资产编号不能超过 50 个字符")
    @Column(name = "asset_code", length = 50, unique = true)
    private String assetCode;

    /** 设备序列号，全局唯一 */
    @Size(max = 100, message = "序列号不能超过 100 个字符")
    @Column(name = "serial_number", length = 100, unique = true)
    private String serialNumber;

    /** 设备状态：在线 / 离线 / 维修中 / 使用中 —— 反映连通性，不是资产状态 */
    @NotBlank(message = "设备状态不能为空")
    @Size(max = 20, message = "设备状态不能超过 20 个字符")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * 资产生命周期状态：正常 / 维修 / 报废 / 停用。
     *
     * <p>允许为空：这张表在加这个字段之前就有数据了。历史数据为 null 时，
     * 服务层和前端按「正常」处理（见 {@link #LIFECYCLE_NORMAL}）。
     */
    @Size(max = 20, message = "生命周期状态不能超过 20 个字符")
    @Column(name = "lifecycle_status", length = 20)
    private String lifecycleStatus = LIFECYCLE_NORMAL;

    /**
     * 所属部门 id，指向 sys_dept。null 表示未分配。
     *
     * <p>和 categoryId 一样存裸 id 而不是 @ManyToOne 关联：
     * 项目开了 open-in-view=false，用关联对象容易在序列化时踩懒加载异常
     * （SysRole.menus 上已经踩过一次），裸 id 也没有 N+1 问题。
     */
    @Column(name = "dept_id")
    private Long deptId;

    /** 型号，如 SHT-2000 */
    @Size(max = 100, message = "型号不能超过 100 个字符")
    @Column(name = "model", length = 100)
    private String model;

    /** 生产厂商 */
    @Size(max = 100, message = "厂商不能超过 100 个字符")
    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    /** 所在位置 */
    @Size(max = 100, message = "位置不能超过 100 个字符")
    @Column(name = "location", length = 100)
    private String location;

    /** 备注描述 */
    @Size(max = 500, message = "描述不能超过 500 个字符")
    @Column(name = "description", length = 500)
    private String description;

    /** 采购日期 */
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** 保修到期日。前端可以据此算出"快过保"清单 */
    @Column(name = "warranty_date")
    private LocalDate warrantyDate;

    /** 当前借用人。为空表示未被借出 */
    @Size(max = 50, message = "借用人不能超过 50 个字符")
    @Column(name = "borrower", length = 50)
    private String borrower;

    /** 借出时间。归还时和 borrower 一起清空 */
    @Column(name = "borrow_time")
    private LocalDateTime borrowTime;

    // ---------- 报废信息 ----------
    // 一台设备只会报废一次，所以这几个字段直接放在设备上，
    // 不像调拨那样需要单独一张历史表。

    /** 报废日期。空表示未报废 */
    @Column(name = "scrap_date")
    private LocalDate scrapDate;

    /** 报废原因 */
    @Size(max = 200, message = "报废原因不能超过 200 个字符")
    @Column(name = "scrap_reason", length = 200)
    private String scrapReason;

    /** 报废操作人 */
    @Size(max = 50, message = "操作人不能超过 50 个字符")
    @Column(name = "scrap_operator", length = 50)
    private String scrapOperator;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
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

    public String getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(String lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
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

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getWarrantyDate() {
        return warrantyDate;
    }

    public void setWarrantyDate(LocalDate warrantyDate) {
        this.warrantyDate = warrantyDate;
    }

    public String getBorrower() {
        return borrower;
    }

    public void setBorrower(String borrower) {
        this.borrower = borrower;
    }

    public LocalDateTime getBorrowTime() {
        return borrowTime;
    }

    public void setBorrowTime(LocalDateTime borrowTime) {
        this.borrowTime = borrowTime;
    }

    public LocalDate getScrapDate() {
        return scrapDate;
    }

    public void setScrapDate(LocalDate scrapDate) {
        this.scrapDate = scrapDate;
    }

    public String getScrapReason() {
        return scrapReason;
    }

    public void setScrapReason(String scrapReason) {
        this.scrapReason = scrapReason;
    }

    public String getScrapOperator() {
        return scrapOperator;
    }

    public void setScrapOperator(String scrapOperator) {
        this.scrapOperator = scrapOperator;
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
