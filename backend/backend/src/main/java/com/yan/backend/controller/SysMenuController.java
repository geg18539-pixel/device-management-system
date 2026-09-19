package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.SysMenuTreeVO;
import com.yan.backend.entity.SysMenu;
import com.yan.backend.service.SysMenuService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理 —— 同时也是**权限点的定义入口**。
 *
 * <p>菜单表承担两种角色：
 * <ul>
 *   <li>menuType = 'M' 目录 / 'F' 菜单：决定前端侧边栏显示什么、路由能去哪里。</li>
 *   <li>menuType = 'B' 按钮：不对应任何界面，纯粹是一个**权限点**，
 *       靠 {@code perms} 字段（如 sys:user:add）跟后端接口上的
 *       {@code @RequirePerm} 对应起来。</li>
 * </ul>
 *
 * <p>所以「新增一个按钮权限」的完整流程是：在这里加一条 menuType=B 的记录，
 * 填上权限标识 → 去「角色管理」把它勾给角色 → 后端接口上标 @RequirePerm。
 * 前两步不用改代码。
 */
@RequirePerm("sys:menu:list")
@RestController
@RequestMapping("/api/system/menus")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    /** GET /api/system/menus/tree —— 树形结构（含按钮节点），给 el-tree 和树形表格用 */
    @GetMapping("/tree")
    public ResponseEntity<Result<List<SysMenuTreeVO>>> tree() {
        return ResponseEntity.ok(Result.success(sysMenuService.tree()));
    }

    /** GET /api/system/menus —— 平铺列表 */
    @GetMapping
    public ResponseEntity<Result<List<SysMenu>>> list() {
        return ResponseEntity.ok(Result.success(sysMenuService.listAll()));
    }

    /** GET /api/system/menus/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysMenu>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysMenuService.findById(id)));
    }

    /** POST /api/system/menus */
    @RequirePerm("sys:menu:add")
    @Log(title = "菜单管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysMenu>> create(@Valid @RequestBody SysMenu menu) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysMenuService.create(menu)));
    }

    /** PUT /api/system/menus/{id} */
    @RequirePerm("sys:menu:edit")
    @Log(title = "菜单管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysMenu>> update(@PathVariable Long id,
                                                  @Valid @RequestBody SysMenu menu) {
        return ResponseEntity.ok(Result.success("修改成功", sysMenuService.update(id, menu)));
    }

    /** DELETE /api/system/menus/{id} */
    @RequirePerm("sys:menu:remove")
    @Log(title = "菜单管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
