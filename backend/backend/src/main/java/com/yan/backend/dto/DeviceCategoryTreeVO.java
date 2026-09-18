package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备分类树节点。
 *
 * <p>DeviceCategory 实体只有 parentId，没有 children，直接返回给前端渲染不出层级，
 * 所以在服务层组好树再返回。和 SysMenuTreeVO 是同一个套路。
 */
public class DeviceCategoryTreeVO {

    private Long id;
    private Long parentId;
    private String categoryName;
    private Integer sortOrder;
    private String remark;

    /** 叶子节点是空列表而不是 null，前端就不用额外判空 */
    private List<DeviceCategoryTreeVO> children = new ArrayList<>();

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

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<DeviceCategoryTreeVO> getChildren() {
        return children;
    }

    public void setChildren(List<DeviceCategoryTreeVO> children) {
        this.children = children;
    }
}
