package com.yan.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 新增 / 编辑用户的请求体。
 *
 * <p>关于 password：**新增时必填，编辑时留空表示不修改**。
 * 这种"条件必填"用 Bean Validation 注解表达不了（注解是静态的），
 * 所以在 Service 里手工判断，见 SysUserServiceImpl.save()。
 */
public class SysUserSaveRequest {

    /** 编辑时必填；新增时由路径决定，这里不用校验 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过 50 个字符")
    private String username;

    @Size(min = 4, max = 64, message = "密码长度需在 4 到 64 个字符之间")
    private String password;

    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过 100 个字符")
    private String email;

    @Size(max = 20, message = "手机号不能超过 20 个字符")
    private String phone;

    @Size(max = 20, message = "状态不能超过 20 个字符")
    private String status;

    /** 要分配给该用户的角色 id 列表，可以为空 */
    private List<Long> roleIds;

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

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
