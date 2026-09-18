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
}
