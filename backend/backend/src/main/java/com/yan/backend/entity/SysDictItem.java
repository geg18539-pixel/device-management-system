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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 字典项（字典类型下的一个可选值）。
 *
 * <p><b>label 和 value 要分清楚，这是这个表最重要的一点：</b>
 * <ul>
 *   <li>{@code itemValue} —— 实际**存进业务表**的值（如 {@code MECH}）。
 *       它是数据的一部分，应该稳定、不该随展示文案改动。</li>
 *   <li>{@code itemLabel} —— 界面上**给人看**的文字（如「机械故障」）。
 *       随时可以改，比如把「机械故障」改成「机械类故障」，历史数据不用动。</li>
 * </ul>
 * 如果只存中文文案、把 label 当 value 用，那么改一次文案，
 * 历史数据就和字典对不上了（工单里存的是"机械故障"，字典里已经改成"机械类故障"，
 * 页面上就会显示成找不到的空值）。所以这张表必须有这两列。
 */
@Entity
@Table(name = "sys_dict_item", indexes = {
        // 查询永远是"按字典类型查"，加个索引
        @Index(name = "idx_dict_item_type", columnList = "dict_type,sort_order")
})
public class SysDictItem {

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_DISABLED = "停用";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属字典类型编码，对应 sys_dict_type.dict_type */
    @NotBlank(message = "字典类型不能为空")
    @Size(max = 50, message = "字典类型不能超过 50 个字符")
    @Column(name = "dict_type", nullable = false, length = 50)
    private String dictType;

    /** 展示文案，如「机械故障」 */
    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签不能超过 100 个字符")
    @Column(name = "item_label", nullable = false, length = 100)
    private String itemLabel;

    /**
     * 实际存储值，如 {@code MECH}。
     *
     * <p>用短编码而不是中文：编码稳定、可读性好（写 SQL 排查时一眼能认），
     * 而且不受展示文案改动影响。
     */
    @NotBlank(message = "字典键值不能为空")
    @Size(max = 100, message = "字典键值不能超过 100 个字符")
    @Column(name = "item_value", nullable = false, length = 100)
    private String itemValue;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 正常 / 停用。**停用的项不出现在下拉里**，但历史数据仍然能正常显示 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_NORMAL;

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

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public void setItemLabel(String itemLabel) {
        this.itemLabel = itemLabel;
    }

    public String getItemValue() {
        return itemValue;
    }

    public void setItemValue(String itemValue) {
        this.itemValue = itemValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
