package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AssignMenusRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysRole;
import com.yan.backend.service.SysRoleService;
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

/** 角色管理。仅 admin 角色可访问（由 JwtInterceptor 校验 @RequireRole）。 */
@RequireRole("admin")
@RestController
@RequestMapping("/api/system/roles")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    /** GET /api/system/roles?pageNum=1&pageSize=10&roleName=xxx */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysRole>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String roleName) {

        return ResponseEntity.ok(Result.success(sysRoleService.page(pageNum, pageSize, roleName)));
    }

    /** GET /api/system/roles/all —— 不分页，给"分配角色"弹窗用 */
    @GetMapping("/all")
    public ResponseEntity<Result<List<SysRole>>> all() {
        return ResponseEntity.ok(Result.success(sysRoleService.listAll()));
    }

    /** GET /api/system/roles/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysRole>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysRoleService.findById(id)));
    }

    /** GET /api/system/roles/{id}/menus —— 角色已拥有的菜单 id，供 el-tree 回显 */
    @GetMapping("/{id}/menus")
    public ResponseEntity<Result<List<Long>>> menuIds(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysRoleService.findMenuIds(id)));
    }

    /** POST /api/system/roles */
    @Log(title = "角色管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysRole>> create(@Valid @RequestBody SysRole role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysRoleService.create(role)));
    }

    /** PUT /api/system/roles/{id} */
    @Log(title = "角色管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysRole>> update(@PathVariable Long id,
                                                  @Valid @RequestBody SysRole role) {
        return ResponseEntity.ok(Result.success("修改成功", sysRoleService.update(id, role)));
    }

    /** PUT /api/system/roles/{id}/menus —— 分配菜单权限 */
    @Log(title = "角色管理", businessType = "UPDATE")
    @PutMapping("/{id}/menus")
    public ResponseEntity<Result<Void>> assignMenus(@PathVariable Long id,
                                                    @RequestBody AssignMenusRequest request) {
        sysRoleService.assignMenus(id, request.getMenuIds());
        return ResponseEntity.ok(Result.success("权限分配成功", null));
    }

    /** DELETE /api/system/roles/{id} */
    @Log(title = "角色管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
