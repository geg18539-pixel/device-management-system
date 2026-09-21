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
     * <p>@JsonIgnore 让这个字段永远不会被序列化进接口响应。
     *
     * <p>这里用 com.fasterxml.jackson.annotation.JsonIgnore 是**正确**的，
     * 尽管 Boot 4 的 Spring MVC 实际用的是 Jackson 3（tools.jackson）。
     * 原因是 Jackson 3 有意把注解保留在 Jackson 2 的坐标和包名下以保持兼容，
     * jackson-databind 3.1.5 的 pom 里对此有明确注释。
     * 会变的是 ObjectMapper 的类路径（tools.jackson.databind.ObjectMapper），
     * 注解本身不变。
     */
    @JsonIgnore
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    /**
     * 密码最后修改时间。
     *
     * <p>用来算"密码还有多少天到期"。**允许为空**：这张表在加这个字段之前就有数据了，
     * 那些行是 NULL。服务层对 NULL 的处理是**按"刚改过"算**（也就是当作还没到期）——
     * 反过来的话，升级完所有老用户一登录就被判定为"密码已过期"，直接进不去系统。
     *
     * <p>注意默认值刻意是 null 而不是 now()：实体上的字段初始值只在**新建对象**时生效，
     * 对已经存在的行没有作用（ddl-auto 只加列不回填），所以不能依赖它。
     */
    @Column(name = "pwd_update_time")
    private LocalDateTime pwdUpdateTime;

    /**
     * 是否必须先修改密码才能使用系统。
     *
     * <p>三种情况置为 true：新建用户、管理员重置密码、**账号仍在使用初始默认密码**。
     * 最后一种由启动时的数据修补贴上（见 SystemDataSeeder），
     * 因为老库里的 admin 是早就存在的行，加列时拿不到这个标记。
     *
     * <p>用 Boolean 而不是 boolean：加列时已有行是 NULL，
     * 拆箱会抛 NPE。判断一律用 {@code Boolean.TRUE.equals(...)}。
     */
    @Column(name = "must_change_password")
    private Boolean mustChangePassword = Boolean.FALSE;

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
     * 所属部门 id，指向 sys_dept。null 表示未分配。
     *
     * <p>存裸 id 而不是 @ManyToOne，理由同 Device.deptId：
     * 避开 open-in-view=false 下的懒加载问题。
     */
    @Column(name = "dept_id")
    private Long deptId;

    /**
     * 最后登录时间 / 最后登录 IP。
     *
     * <p>由登录接口在认证成功后写入（见 AuthServiceImpl）。允许为空：
     * 这张表在加这两个字段之前就有数据了，而且新建但从未登录过的账号本来也没有值。
     */
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Size(max = 50, message = "IP 不能超过 50 个字符")
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    /**
     * 用户拥有的角色。
     *
     * <p>用 LAZY 加载。这里要特别注意：application.yml 里开了 open-in-view=false，
     * 所以**在事务外访问 roles 会抛 LazyInitializationException**。
     * 登录时用 SysUserRepository.findByUsernameWithRoles() 通过 EntityGraph
     * 一次性把角色查出来，避免踩这个坑。
     */
    @JsonIgnore
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

    public LocalDateTime getPwdUpdateTime() {
        return pwdUpdateTime;
    }

    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) {
        this.pwdUpdateTime = pwdUpdateTime;
    }

    /** 是否需要强制改密。null 一律当 false，避免拆箱 NPE（加列时老数据是 NULL） */
    public boolean isMustChangePassword() {
        return Boolean.TRUE.equals(mustChangePassword);
    }

    public void setMustChangePassword(Boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
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
