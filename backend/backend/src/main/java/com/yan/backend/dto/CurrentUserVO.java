package com.yan.backend.dto;

import java.util.Set;

/**
 * 当前登录用户（GET /api/auth/me 的返回）。
 *
 * <p>比 token 里能解析出的 {@code LoginUser} 多了两样只有服务端才知道的东西：
 * <ul>
 *   <li>{@code perms} —— 细粒度权限点集合，前端用它做**按钮级**显示隐藏。
 *       不放进 token 是因为权限点会随功能增长，token 会越来越大，
 *       而且管理员改完权限后旧 token 里的集合就过期了。</li>
 *   <li>{@code passwordExpireDays} —— 密码还有多少天到期，前端提前提醒。</li>
 * </ul>
 *
 * <p>注意 {@code perms} 对**超管是空集合**也不影响使用：前端的 v-perm 指令
 * 和这里的语义保持一致 —— 有 admin 角色就整体放行。
 * 这样管理员永远不会因为"忘了给 admin 勾新权限"而被锁在新功能外面。
 */
public class CurrentUserVO {

    private Long userId;
    private String username;
    private String nickname;
    private Set<String> roles;

    /** 细粒度权限点，如 sys:user:add、dev:device:remove */
    private Set<String> perms;

    /** 是否必须先修改密码才能使用系统 */
    private boolean mustChangePassword;

    /**
     * 密码还有多少天到期。null 表示"不适用/永不过期"（比如账号没记录过改密时间）。
     * 负数表示已经过期。
     */
    private Long passwordExpireDays;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public Set<String> getPerms() {
        return perms;
    }

    public void setPerms(Set<String> perms) {
        this.perms = perms;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public Long getPasswordExpireDays() {
        return passwordExpireDays;
    }

    public void setPasswordExpireDays(Long passwordExpireDays) {
        this.passwordExpireDays = passwordExpireDays;
    }
}
