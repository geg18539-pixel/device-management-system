package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点。
 *
 * <p>和 SysMenuTreeVO / DeviceCategoryTreeVO 是同一个套路：实体只有 parentId，
 * 没有 children，直接返回给 el-tree 或树形表格渲染不出层级，所以在服务层组好树。
 */
public class DeptTreeVO {

    private Long id;
    private Long parentId;
    private String deptName;
    private Integer sortOrder;
    private String leader;
    private String phone;
    private String status;
    private String remark;

    /** 叶子节点是空列表而不是 null，前端就不用额外判空 */
    private List<DeptTreeVO> children = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
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

    public List<DeptTreeVO> getChildren() {
        return children;
    }

    public void setChildren(List<DeptTreeVO> children) {
        this.children = children;
    }
}
