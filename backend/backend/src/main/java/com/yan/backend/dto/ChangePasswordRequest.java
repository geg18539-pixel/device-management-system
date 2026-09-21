package com.yan.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户自己修改密码。
 *
 * <p><b>必须提供原密码</b>，即使当前是"被强制改密"的状态。
 * 强制改密的场景下（比如密码是管理员重置的）用户是知道那个临时密码的，
 * 让他再输一遍并不麻烦，但能挡住"别人趁他没锁屏，直接给他改掉密码"这种情况。
 */
public class ChangePasswordRequest {

    @NotBlank(message = "请输入原密码")
    @Size(max = 64, message = "原密码长度不正确")
    private String oldPassword;

    /**
     * 新密码。
     *
     * <p>这里只做长度上限校验，**强度校验在 Service 里做**：
     * 强度规则要求"四类字符至少占三类"，用注解表达不了，
     * 而且校验逻辑已经收在 PasswordPolicy 里，不该在这里再抄一份。
     */
    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 64, message = "新密码长度需在 8 到 64 个字符之间")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
