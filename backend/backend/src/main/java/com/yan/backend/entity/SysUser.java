package com.yan.backend.entity;

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
 * 系统用户。
 *
 * <p>与 SysRole 是多对多。这里用 @ManyToMany + @JoinTable 让 Hibernate 建出
 * sys_user_role 关联表 —— 注意**不要再额外写一个 SysUserRole 实体去映射同一张表**，
 * 那样两个映射会打架，Hibernate 生成的 DDL 会冲突或直接报错。
 * 关联表在数据库里是真实存在的，只是没有对应的 Java 类。
 */
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过 50 个字符")
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * BCrypt 加密后的密码。
     *
     * <p>注意：这个类目前不会被直接序列化成 JSON 返回（登录接口用的是 LoginResponse DTO）。
     * 等以后有接口要返回用户对象时，必须先把密码置空或加 @JsonIgnore，
     * 否则会把密码哈希泄露出去。
     */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Size(max = 50, message = "昵称不能超过 50 个字符")
    @Column(name = "nickname", length = 50)
    private String nickname;

    @Size(max = 100, message = "邮箱不能超过 100 个字符")
    @Column(name = "email", length = 100)
    private String email;

    @Size(max = 20, message = "手机号不能超过 20 个字符")
    @Column(name = "phone", length = 20)
    private String phone;

    /** 账号状态：正常 / 停用 */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "正常";

    /**
     * 用户拥有的角色。
     *
     * <p>用 LAZY 加载。这里要特别注意：application.yml 里开了 open-in-view=false，
     * 所以**在事务外访问 roles 会抛 LazyInitializationException**。
     * 登录时用 SysUserRepository.findByUsernameWithRoles() 通过 EntityGraph
     * 一次性把角色查出来，避免踩这个坑。
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<SysRole> roles = new HashSet<>();

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public Set<SysRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<SysRole> roles) {
        this.roles = roles;
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
