package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AssignRolesRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;
import com.yan.backend.service.SysUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户管理。
 *
 * <p><b>权限控制方式</b>：类上标 {@code @RequirePerm("sys:user:list")} 作为默认，
 * 覆盖所有读接口；写接口各自标更具体的权限标识。方法级注解优先于类级，
 * 所以不用给每个读方法重复标注。
 *
 * <p>权限标识来自菜单表里 menuType='B' 的按钮记录，由管理员在
 * 「角色管理 → 分配权限」里勾选分配。<b>不用改代码就能调整谁能做什么</b> ——
 * 这是相比原先 {@code @RequireRole("admin")} 的关键区别。
 *
 * <p>注意：拥有 admin 角色的用户会绕过所有权限检查（见 JwtInterceptor），
 * 所以验证细粒度权限要用 operator 这类非超管账号。
 */
@RequirePerm("sys:user:list")
@RestController
@RequestMapping("/api/system/users")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /** GET /api/system/users?pageNum=1&pageSize=10&username=xxx */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysUserVO>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String username) {

        return ResponseEntity.ok(Result.success(sysUserService.page(pageNum, pageSize, username)));
    }

    /** GET /api/system/users/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysUserVO>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysUserService.findById(id)));
    }

    /** GET /api/system/users/{id}/roles —— 供"分配角色"弹窗回显勾选状态 */
    @GetMapping("/{id}/roles")
    public ResponseEntity<Result<List<Long>>> roleIds(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysUserService.findRoleIds(id)));
    }

    /** POST /api/system/users */
    @RequirePerm("sys:user:add")
    @Log(title = "用户管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysUserVO>> create(@Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysUserService.create(request)));
    }

    /** PUT /api/system/users/{id} */
    @RequirePerm("sys:user:edit")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysUserVO>> update(@PathVariable Long id,
                                                    @Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.ok(Result.success("修改成功", sysUserService.update(id, request)));
    }

    /** PUT /api/system/users/{id}/roles —— 分配角色 */
    @RequirePerm("sys:user:assign")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/roles")
    public ResponseEntity<Result<Void>> assignRoles(@PathVariable Long id,
                                                    @RequestBody AssignRolesRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    /** PUT /api/system/users/{id}/password —— 重置密码 */
    @RequirePerm("sys:user:reset")
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/password")
    public ResponseEntity<Result<Void>> resetPassword(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("password"));
        return ResponseEntity.ok(Result.success("密码重置成功", null));
    }

    /** DELETE /api/system/users/{id} */
    @RequirePerm("sys:user:remove")
    @Log(title = "用户管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
