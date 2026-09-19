package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
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

/**
 * 角色管理。
 *
 * <p>角色是"权限的集合"：给角色分配菜单和按钮权限，再把角色给用户。
 * 单独给某个用户调权限请改他的角色，而不是直接改角色 —— 角色是复用的。
 */
@RequirePerm("sys:role:list")
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

    /** GET /api/system/roles/{id}/menus —— 角色已拥有的菜单+按钮 id，供 el-tree 回显 */
    @GetMapping("/{id}/menus")
    public ResponseEntity<Result<List<Long>>> menuIds(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysRoleService.findMenuIds(id)));
    }

    /** POST /api/system/roles */
    @RequirePerm("sys:role:add")
    @Log(title = "角色管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysRole>> create(@Valid @RequestBody SysRole role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysRoleService.create(role)));
    }

    /** PUT /api/system/roles/{id} */
    @RequirePerm("sys:role:edit")
    @Log(title = "角色管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysRole>> update(@PathVariable Long id,
                                                  @Valid @RequestBody SysRole role) {
        return ResponseEntity.ok(Result.success("修改成功", sysRoleService.update(id, role)));
    }

    /**
     * PUT /api/system/roles/{id}/menus —— 分配权限（菜单 + 按钮）。
     *
     * <p>传的是菜单/按钮 id 的**全量列表**。前端 el-tree 勾选后提交完整集合，
     * 并且要把**半选的父节点也带上**，否则父级目录会丢。
     */
    @RequirePerm("sys:role:assign")
    @Log(title = "角色管理", businessType = "UPDATE")
    @PutMapping("/{id}/menus")
    public ResponseEntity<Result<Void>> assignMenus(@PathVariable Long id,
                                                    @RequestBody AssignMenusRequest request) {
        sysRoleService.assignMenus(id, request.getMenuIds());
        return ResponseEntity.ok(Result.success("权限分配成功", null));
    }

    /**
     * POST /api/system/roles/{id}/copy —— 复制角色（含它已分配的菜单和按钮权限）。
     *
     * <p>用途：新建一个和现有角色权限差不多的角色时，不用从头一条条勾。
     */
    @RequirePerm("sys:role:add")
    @Log(title = "角色管理", businessType = "INSERT")
    @PostMapping("/{id}/copy")
    public ResponseEntity<Result<SysRole>> copy(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("复制成功", sysRoleService.copy(id)));
    }

    /** DELETE /api/system/roles/{id} */
    @RequirePerm("sys:role:remove")
    @Log(title = "角色管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
