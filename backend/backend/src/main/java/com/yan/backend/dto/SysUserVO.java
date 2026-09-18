package com.yan.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表 / 详情的返回结构。
 *
 * <p>为什么不直接返回 SysUser 实体：SysUser.roles 是 LAZY 的，而项目里开了
 * open-in-view=false，事务一结束 Session 就关了。Jackson 在 Controller 层
 * 序列化实体时去碰 getRoles() 会直接抛 LazyInitializationException。
 *
 * <p>所以在 Service 的事务内就把实体映射成这个 VO（此时懒加载还能正常触发），
 * 顺便把密码这类不该外露的字段结构性地排除掉 —— 比依赖 @JsonIgnore 更保险。
 */
public class SysUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime createTime;

    /** 角色 id 列表，供"分配角色"弹窗回显勾选状态 */
    private List<Long> roleIds;

    /** 角色名称列表，供表格直接展示 */
    private List<String> roleNames;

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public void setRoleNames(List<String> roleNames) {
        this.roleNames = roleNames;
    }
}
