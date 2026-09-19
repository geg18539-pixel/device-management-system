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
 * 系统菜单 / 权限。
 *
 * <p>树形结构用 parentId 自关联表达，没有用 @ManyToMany 自关联。
 * 原因是自关联集合在这种"查一棵树"的场景下容易出现 N+1 和递归序列化的坑，
 * 而 parentId 平铺存储、由前端或服务层组树，简单可控。
 * parentId 为 null 表示顶级菜单。
 */
@Entity
@Table(name = "sys_menu")
public class SysMenu {

    /** 顶级菜单的 parentId 约定值 */
    public static final Long ROOT_PARENT_ID = 0L;

    // ---------- 菜单类型常量 ----------
    /** 目录：侧边栏上可展开的父级，没有自己的页面 */
    public static final String TYPE_DIR = "M";
    /** 菜单：对应一个前端页面 */
    public static final String TYPE_MENU = "F";
    /**
     * 按钮：不对应任何界面，纯粹是一个**权限点**。
     *
     * <p>它不出现在侧边栏里，作用是把 perms 字段（如 sys:user:add）
     * 和后端接口上的 @RequirePerm 对应起来，实现按钮级的细粒度权限控制。
     */
    public static final String TYPE_BUTTON = "B";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称不能超过 50 个字符")
    @Column(name = "menu_name", nullable = false, length = 50)
    private String menuName;

    /** 父菜单 id，0 表示顶级 */
    @Column(name = "parent_id", nullable = false)
    private Long parentId = ROOT_PARENT_ID;

    /** 前端路由路径，目录/菜单类型才有 */
    @Size(max = 200, message = "路由路径不能超过 200 个字符")
    @Column(name = "path", length = 200)
    private String path;

    /** 前端组件路径 */
    @Size(max = 200, message = "组件路径不能超过 200 个字符")
    @Column(name = "component", length = 200)
    private String component;

    /**
     * 菜单类型：
     * M = 目录，F = 菜单，B = 按钮（按钮只做权限控制，不出现在侧边栏）
     */
    @NotBlank(message = "菜单类型不能为空")
    @Column(name = "menu_type", nullable = false, length = 1)
    private String menuType;

    /** 权限标识，如 device:list、sys:user:add，配合 @PreAuthorize 之类的注解使用 */
    @Size(max = 100, message = "权限标识不能超过 100 个字符")
    @Column(name = "perms", length = 100)
    private String perms;

    @Size(max = 50, message = "图标不能超过 50 个字符")
    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 是否在侧边栏显示：1 显示，0 隐藏 */
    @Column(name = "visible", nullable = false)
    private Integer visible = 1;

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

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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
