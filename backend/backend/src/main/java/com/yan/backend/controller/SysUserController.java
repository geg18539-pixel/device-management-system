package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequireRole;
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
 * <p>写操作都标了 @Log，会被 LogAspect 拦截并异步记入 sys_oper_log；
 * 查询接口不记，免得日志表被 GET 请求刷爆。
 *
 * <p>类上的 @RequireRole("admin") 由 JwtInterceptor 校验：非 admin 角色访问会拿到 403。
 * 这是接口层的真实保护，和前端侧边栏隐藏菜单不是一回事 —— 后者只是界面效果。
 *
 * <p>注意它只保护了本 Controller。以后在 /api/system 下新增控制器时，
 * 要记得同样加上 @RequireRole，否则新接口默认是"登录即可访问"的。
 */
@RequireRole("admin")
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
    @Log(title = "用户管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysUserVO>> create(@Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysUserService.create(request)));
    }

    /** PUT /api/system/users/{id} */
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysUserVO>> update(@PathVariable Long id,
                                                    @Valid @RequestBody SysUserSaveRequest request) {
        return ResponseEntity.ok(Result.success("修改成功", sysUserService.update(id, request)));
    }

    /** PUT /api/system/users/{id}/roles —— 分配角色 */
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/roles")
    public ResponseEntity<Result<Void>> assignRoles(@PathVariable Long id,
                                                    @RequestBody AssignRolesRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return ResponseEntity.ok(Result.success("角色分配成功", null));
    }

    /** PUT /api/system/users/{id}/password —— 重置密码 */
    @Log(title = "用户管理", businessType = "UPDATE")
    @PutMapping("/{id}/password")
    public ResponseEntity<Result<Void>> resetPassword(@PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("password"));
        return ResponseEntity.ok(Result.success("密码重置成功", null));
    }

    /** DELETE /api/system/users/{id} */
    @Log(title = "用户管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
