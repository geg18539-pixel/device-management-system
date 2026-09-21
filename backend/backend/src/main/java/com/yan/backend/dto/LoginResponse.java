package com.yan.backend.dto;

import java.util.Set;

/**
 * 登录响应。
 *
 * <p>用专门的 DTO 而不是直接返回 SysUser 实体，一是不让密码哈希泄露出去，
 * 二是响应结构可以按前端需要裁剪，不受实体字段变动影响。
 */
public class LoginResponse {

    /** JWT，前端之后每次请求放在 Authorization 头里 */
    private String token;

    /** 固定 Bearer，前端拼请求头时用：Authorization: Bearer <token> */
    private String tokenType = "Bearer";

    /** token 有效期，单位秒 */
    private long expiresIn;

    private Long userId;

    private String username;

    private String nickname;

    /** 角色标识集合，如 ["admin"]，前端据此过滤菜单 */
    private Set<String> roles;

    /**
     * 细粒度权限点集合，前端据此做**按钮级**显示隐藏。
     *
     * <p>登录时就返回，省得前端登录后再补一次 /auth/me 请求。
     */
    private Set<String> perms;

    /**
     * 是否需要强制修改密码。
     *
     * <p>为 true 时前端必须跳到改密页，且**后端会拦住其它所有接口**（返回 428）——
     * 只靠前端跳转是不够的，绕过前端直接调接口就能继续用系统。
     */
    private boolean mustChangePassword;

    /** 密码还有多少天到期（负数表示已过期）。null 表示不适用 */
    private Long passwordExpireDays;

    /** 密码是否已经过期。过期和"被强制改密"都要求改密，但提示文案不同 */
    private boolean passwordExpired;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

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

    public boolean isPasswordExpired() {
        return passwordExpired;
    }

    public void setPasswordExpired(boolean passwordExpired) {
        this.passwordExpired = passwordExpired;
    }
}
