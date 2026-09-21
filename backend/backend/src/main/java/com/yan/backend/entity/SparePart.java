package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配件 / 耗材（库存主数据）。
 *
 * <p>库存量 {@link #stockQuantity} **不由用户直接编辑**，只能通过出入库流水
 * （{@link SparePartRecord}）改动。理由：让用户手工改库存数字，
 * 用不了多久就和实际对不上，而且查不出是谁改的。所有变动必须走流水，
 * 这样"现在库存多少"永远等于"历次出入库累加"，也能回答"这批货去哪了"。
 */
@Entity
@Table(name = "spare_part")
public class SparePart {

    public static final String STATUS_ENABLED = "启用";
    public static final String STATUS_DISABLED = "停用";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配件编码，全局唯一（如 PJ-0001） */
    @NotBlank(message = "配件编码不能为空")
    @Size(max = 50, message = "配件编码不能超过 50 个字符")
    @Column(name = "part_code", nullable = false, length = 50, unique = true)
    private String partCode;

    @NotBlank(message = "配件名称不能为空")
    @Size(max = 100, message = "配件名称不能超过 100 个字符")
    @Column(name = "part_name", nullable = false, length = 100)
    private String partName;

    /** 规格型号 */
    @Size(max = 100, message = "规格型号不能超过 100 个字符")
    @Column(name = "model", length = 100)
    private String model;

    /** 计量单位：个 / 米 / 套 / 卷 */
    @Size(max = 20, message = "单位不能超过 20 个字符")
    @Column(name = "unit", length = 20)
    private String unit;

    /**
     * 当前库存数量。
     *
     * <p>由出入库服务维护，**没有对应的外部写接口**。
     * 允许为 0，但出库时不能扣成负数（服务层拦截）。
     */
    @Min(value = 0, message = "库存数量不能为负")
    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    /**
     * 库存预警阈值。
     *
     * <p>库存 ≤ 这个值就在列表里标红、并在看板/列表顶部提示。
     * 阈值允许为 0（表示不预警）。
     */
    @Min(value = 0, message = "预警阈值不能为负")
    @Column(name = "warn_threshold", nullable = false)
    private Integer warnThreshold = 0;

    /** 参考单价，用于估算工单成本 */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Size(max = 100, message = "供应商不能超过 100 个字符")
    @Column(name = "supplier", length = 100)
    private String supplier;

    /** 库位 */
    @Size(max = 100, message = "库位不能超过 100 个字符")
    @Column(name = "location", length = 100)
    private String location;

    /** 启用 / 停用。停用的配件不出现在出入库的下拉里 */
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

    public String getPartCode() {
        return partCode;
    }

    public void setPartCode(String partCode) {
        this.partCode = partCode;
    }

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getWarnThreshold() {
        return warnThreshold;
    }

    public void setWarnThreshold(Integer warnThreshold) {
        this.warnThreshold = warnThreshold;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
