package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.SysUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户选项（给"指派维修人""维保负责人"这类下拉用）。
 *
 * <p><b>为什么不复用 /api/system/users？</b>那个接口在 {@code /api/system/**} 下，
 * 类上标了 {@code @RequireRole("admin")} —— 普通操作员指派工单时拿不到用户列表，
 * 下拉就是空的。所以这里单开一个只要求登录的接口。
 *
 * <p><b>只返回 id / username / nickname，不返回邮箱、手机、角色、状态等</b>：
 * 这个接口所有登录用户都能调，暴露太多字段等于把通讯录开放了。
 */
@RestController
@RequestMapping("/api/users")
public class UserOptionController {

    private final SysUserRepository sysUserRepository;

    public UserOptionController(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    /** GET /api/users/options —— 启用中的用户，给下拉用 */
    @GetMapping("/options")
    public ResponseEntity<Result<List<Map<String, Object>>>> options() {
        List<Map<String, Object>> options = sysUserRepository.findAll().stream()
                // 停用的账号不该能被指派
                .filter(u -> "正常".equals(u.getStatus()))
                .sorted(Comparator.comparing(SysUser::getUsername))
                .map(user -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    // ★ value 用 **username**：工单/维保里存的是用户名字符串，
                    // 通知按用户名就能精确解析到账号（不像昵称可能重复）。
                    option.put("value", user.getUsername());
                    // label 给下拉用（带用户名，便于区分同名的人）
                    option.put("label", buildLabel(user));
                    // name 给表格展示用（只要昵称，避免表格里挤满「王强（wangqiang）」）
                    option.put("name", displayName(user));
                    return option;
                })
                .toList();
        return ResponseEntity.ok(Result.success(options));
    }

    /** 下拉里显示「昵称（用户名）」；没填昵称就只显示用户名 */
    private String buildLabel(SysUser user) {
        String nickname = user.getNickname();
        if (nickname == null || nickname.isBlank() || nickname.equals(user.getUsername())) {
            return user.getUsername();
        }
        return nickname + "（" + user.getUsername() + "）";
    }

    /** 表格展示用的名字：优先昵称，没有就退回用户名 */
    private String displayName(SysUser user) {
        String nickname = user.getNickname();
        return (nickname == null || nickname.isBlank()) ? user.getUsername() : nickname;
    }
}
