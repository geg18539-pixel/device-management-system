package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeptTreeVO;
import com.yan.backend.entity.SysDept;
import com.yan.backend.service.SysDeptService;
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
 * 部门管理。
 *
 * <p>部门是设备和用户归属的基础数据，所以**读接口不限制权限**（只要求登录）——
 * 设备列表的"按部门筛选"、用户表单的"所属部门"下拉都要用它，
 * 如果连读都要特殊权限，普通操作员用设备页时会拿不到部门选项。
 *
 * <p>写操作才要求权限，且允许额外配置：
 * 类上没有 @RequirePerm，方法上各自标注，这样读接口自然放行。
 */
@RestController
@RequestMapping("/api/system/depts")
public class SysDeptController {

    private final SysDeptService sysDeptService;

    public SysDeptController(SysDeptService sysDeptService) {
        this.sysDeptService = sysDeptService;
    }

    /** GET /api/system/depts/tree —— 树形结构，给部门树和下拉用 */
    @GetMapping("/tree")
    public ResponseEntity<Result<List<DeptTreeVO>>> tree() {
        return ResponseEntity.ok(Result.success(sysDeptService.tree()));
    }

    /** GET /api/system/depts —— 平铺列表 */
    @GetMapping
    public ResponseEntity<Result<List<SysDept>>> list() {
        return ResponseEntity.ok(Result.success(sysDeptService.listAll()));
    }

    /** GET /api/system/depts/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysDept>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sysDeptService.findById(id)));
    }

    /** POST /api/system/depts */
    @RequirePerm("sys:dept:add")
    @Log(title = "部门管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<SysDept>> create(@Valid @RequestBody SysDept dept) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysDeptService.create(dept)));
    }

    /** PUT /api/system/depts/{id} */
    @RequirePerm("sys:dept:edit")
    @Log(title = "部门管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SysDept>> update(@PathVariable Long id,
                                                  @Valid @RequestBody SysDept dept) {
        return ResponseEntity.ok(Result.success("修改成功", sysDeptService.update(id, dept)));
    }

    /** DELETE /api/system/depts/{id} */
    @RequirePerm("sys:dept:remove")
    @Log(title = "部门管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sysDeptService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
