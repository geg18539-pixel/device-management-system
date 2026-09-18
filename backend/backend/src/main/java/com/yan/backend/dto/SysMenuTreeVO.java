package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。
 *
 * <p>SysMenu 实体只有 parentId，没有 children 字段，直接返回给 Element Plus
 * 的 el-tree 是渲染不出层级的 —— 那个组件要求数据本身是嵌套结构。
 * 所以在服务层把平铺列表组装成树再返回。
 */
public class SysMenuTreeVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String path;
    private String component;
    private String menuType;
    private String perms;
    private String icon;
    private Integer sortOrder;
    private Integer visible;

    /** 子节点；叶子节点是空列表而不是 null，前端就不用额外判空 */
    private List<SysMenuTreeVO> children = new ArrayList<>();

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

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getPerms() {
        return perms;
    }

    public void setPerms(String perms) {
        this.perms = perms;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    public List<SysMenuTreeVO> getChildren() {
        return children;
    }

    public void setChildren(List<SysMenuTreeVO> children) {
        this.children = children;
    }
}
