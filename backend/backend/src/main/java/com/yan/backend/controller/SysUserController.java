package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
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
 * <p><b>当前已知的权限缺口</b>：这些接口只校验了"有没有登录"（由 JwtInterceptor 负责），
 * <b>没有校验"是不是管理员"</b>。也就是说 operator 角色拿着 token 也能调这些接口建账号。
 * 前端侧边栏会把菜单藏起来，但那只是界面上的隐藏，不能当安全措施。
 * 要补上需要在拦截器或 Controller 加角色校验（比如自定义一个 @RequireRole 注解），
 * 目前刻意没做，避免超出本轮范围。
 */
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
