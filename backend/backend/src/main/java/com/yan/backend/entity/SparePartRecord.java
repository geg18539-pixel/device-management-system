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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配件出入库流水。
 *
 * <p>每条记录代表一次入库或出库，并保存**变动前后的库存快照**。
 *
 * <p>为什么要存前后快照，而不是只存"变动数量"？
 * 只存增量的话，任何一次盘点差异都会让整条历史对不上账，
 * 而且出问题时无法回答"这笔操作当时账上到底有多少"。
 * 有了快照，每条流水自身就是完整的、可独立核对的事实。
 *
 * <p>记录**只增不改不删**，属于追溯性资料。
 */
@Entity
@Table(name = "spare_part_record", indexes = {
        @Index(name = "idx_part_record_part", columnList = "part_id,record_time"),
        // 详情页要查"某张工单消耗了哪些配件"
        @Index(name = "idx_part_record_repair", columnList = "related_repair_id")
})
public class SparePartRecord {

    /** 入库 */
    public static final String TYPE_IN = "入库";
    /** 出库 */
    public static final String TYPE_OUT = "出库";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配件 id */
    @Column(name = "part_id", nullable = false)
    private Long partId;

    /** 配件编码 / 名称快照，流水是历史资料，不该受后续改名影响 */
    @Size(max = 50, message = "配件编码不能超过 50 个字符")
    @Column(name = "part_code", length = 50)
    private String partCode;

    @Size(max = 100, message = "配件名称不能超过 100 个字符")
    @Column(name = "part_name", length = 100)
    private String partName;

    /** 入库 / 出库 */
    @NotBlank(message = "出入库类型不能为空")
    @Column(name = "record_type", nullable = false, length = 10)
    private String recordType;

    /** 本次变动数量，**始终为正数**，方向由 recordType 表达 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 变动前库存 */
    @Column(name = "before_stock", nullable = false)
    private Integer beforeStock;

    /** 变动后库存 */
    @Column(name = "after_stock", nullable = false)
    private Integer afterStock;

    /**
     * 关联的维修工单 id。出库时如果这批配件是修某台设备用掉的，就填上。
     *
     * <p>这是"维修工单关联消耗配件"的落点：设备详情页的"配件更换记录"
     * 就是反查 related_repair_id 或按设备找工单再找流水。
     * 入库时通常为空。
     */
    @Column(name = "related_repair_id")
    private Long relatedRepairId;

    /** 本次单价。入库按实际采购价，出库取当时的参考单价，用于估算成本 */
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Size(max = 50, message = "操作人不能超过 50 个字符")
    @Column(name = "operator", length = 50)
    private String operator;

    @Column(name = "record_time", nullable = false)
    private LocalDateTime recordTime;

    @Size(max = 200, message = "备注不能超过 200 个字符")
    @Column(name = "remark", length = 200)
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

    public Long getPartId() {
        return partId;
    }

    public void setPartId(Long partId) {
        this.partId = partId;
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

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getBeforeStock() {
        return beforeStock;
    }

    public void setBeforeStock(Integer beforeStock) {
        this.beforeStock = beforeStock;
    }

    public Integer getAfterStock() {
        return afterStock;
    }

    public void setAfterStock(Integer afterStock) {
        this.afterStock = afterStock;
    }

    public Long getRelatedRepairId() {
        return relatedRepairId;
    }

    public void setRelatedRepairId(Long relatedRepairId) {
        this.relatedRepairId = relatedRepairId;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
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
