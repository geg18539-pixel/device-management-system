package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 字典类型（一组枚举值的定义，如「故障类型」）。
 *
 * <p><b>为什么要有字典：</b>像"故障类型"这种枚举，硬编码在前端的话，
 * 每次调整（加一类"供电异常"）都要改代码、重新构建前端。
 * 放进字典之后管理员在界面上加一条就生效。
 *
 * <p><b>⚠️ 状态机的状态值不要放进字典。</b>设备状态（正常/维修/报废/停用）、
 * 工单状态（待受理/维修中/已完成/已关闭）这些**参与逻辑判断**的值，
 * 流转规则是写在代码里的。如果它们变成可随意编辑的字典项，
 * 有人把"维修中"改成"修理中"，所有判断和统计都会**静默失效** ——
 * 不报错，只是数据全对不上，这种问题极难排查。
 * 字典适合的是"纯分类、不参与判断"的枚举。
 */
@Entity
@Table(name = "sys_dict_type")
public class SysDictType {

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_DISABLED = "停用";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 字典名称，给人看的，如「故障类型」 */
    @NotBlank(message = "字典名称不能为空")
    @Size(max = 100, message = "字典名称不能超过 100 个字符")
    @Column(name = "dict_name", nullable = false, length = 100)
    private String dictName;

    /**
     * 字典类型编码，全局唯一，如 {@code fault_type}。
     *
     * <p>这是**代码里引用它的标识**，所以限制成小写字母/数字/下划线 ——
     * 带上中文或空格之后在 URL、代码常量里都不好用。
     * 创建后不允许修改（改了等于把这个字典的所有引用都断了）。
     */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 50, message = "字典类型编码不能超过 50 个字符")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$",
            message = "字典类型编码只能用小写字母、数字和下划线，且以字母开头")
    @Column(name = "dict_type", nullable = false, unique = true, length = 50)
    private String dictType;

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

    public String getDictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
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
