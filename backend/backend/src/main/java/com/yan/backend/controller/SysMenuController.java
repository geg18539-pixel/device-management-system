package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
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

@RestController
@RequestMapping("/api/system/menus")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    /** GET /api/system/menus/tree —— 树形结构，给 el-tree 和树形表格用 */
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
    @Log(title = "菜单管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysMenu>> create(@Valid @RequestBody SysMenu menu) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysMenuService.create(menu)));
    }

    /** PUT /api/system/menus/{id} */
    @Log(title = "菜单管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysMenu>> update(@PathVariable Long id,
                                                  @Valid @RequestBody SysMenu menu) {
        return ResponseEntity.ok(Result.success("修改成功", sysMenuService.update(id, menu)));
    }

    /** DELETE /api/system/menus/{id} */
    @Log(title = "菜单管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
