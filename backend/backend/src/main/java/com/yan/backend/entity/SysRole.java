package com.yan.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 系统角色。
 *
 * <p>与 SysUser 多对多（拥有者是 SysUser，这里不写 mappedBy，
 * 只由 SysUser 一侧维护 sys_user_role）。
 * 与 SysMenu 多对多，本类是拥有者，负责维护 sys_role_menu。
 */
@Entity
@Table(name = "sys_role")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    /** 角色标识，如 admin / operator，代码里判断权限用这个 */
    @NotBlank(message = "角色标识不能为空")
    @Size(max = 50, message = "角色标识不能超过 50 个字符")
    @Column(name = "role_key", nullable = false, unique = true, length = 50)
    private String roleKey;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** 状态：正常 / 停用 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "正常";

    @Size(max = 500, message = "备注不能超过 500 个字符")
    @Column(name = "remark", length = 500)
    private String remark;

    /**
     * 角色拥有的菜单权限。LAZY 加载，只在事务内访问。
     *
     * <p>@JsonIgnore 是必须的：角色实体会被直接返回给前端（比如 /roles/all
     * 给"分配角色"弹窗用），而 Jackson 是在事务外的 Controller 层做序列化的，
     * 去碰这个未初始化的懒集合会抛 LazyInitializationException。
     * 需要菜单 id 的地方（findMenuIds）是在 Service 的事务内访问的，不受影响。
     */
    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_role_menu",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "menu_id")
    )
    private Set<SysMenu> menus = new HashSet<>();

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

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
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

    public Set<SysMenu> getMenus() {
        return menus;
    }

    public void setMenus(Set<SysMenu> menus) {
        this.menus = menus;
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
