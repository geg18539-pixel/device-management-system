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
 * 部门（树形）。
 *
 * <p>树形结构和 SysMenu / DeviceCategory 一样用 parentId 自关联表达，
 * parentId 为 0 表示顶级。企业内部部门本身就是层级结构（公司 → 中心 → 部门 → 组），
 * 所以这里必须支持任意层级，不能只用一层。
 *
 * <p>设备（Device.deptId）和用户（SysUser.deptId）都挂到部门上，
 * 所以删除前必须检查有没有设备/用户/子部门在用。
 */
@Entity
@Table(name = "sys_dept")
public class SysDept {

    /** 顶级部门的 parentId 约定值 */
    public static final Long ROOT_PARENT_ID = 0L;

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_DISABLED = "停用";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称不能超过 50 个字符")
    @Column(name = "dept_name", nullable = false, length = 50)
    private String deptName;

    /** 上级部门 id，0 表示顶级 */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = ROOT_PARENT_ID;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 部门负责人 */
    @Size(max = 50, message = "负责人不能超过 50 个字符")
    @Column(name = "leader", length = 50)
    private String leader;

    @Size(max = 20, message = "联系电话不能超过 20 个字符")
    @Column(name = "phone", length = 20)
    private String phone;

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

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
