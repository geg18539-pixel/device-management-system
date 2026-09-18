package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceCategoryTreeVO;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.service.DeviceCategoryService;
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
 * 设备分类管理。
 *
 * <p>没有加 @RequireRole —— 分类属于基础数据，普通操作员也需要维护。
 * 如果想把管理动作收窄到管理员，在写操作上单独标 @RequireRole("admin") 即可
 * （方法级注解优先于类级）。
 */
@RestController
@RequestMapping("/api/device-categories")
public class DeviceCategoryController {

    private final DeviceCategoryService deviceCategoryService;

    public DeviceCategoryController(DeviceCategoryService deviceCategoryService) {
        this.deviceCategoryService = deviceCategoryService;
    }

    /** GET /api/device-categories/tree —— 树形，给分类下拉和树形表格用 */
    @GetMapping("/tree")
    public ResponseEntity<Result<List<DeviceCategoryTreeVO>>> tree() {
        return ResponseEntity.ok(Result.success(deviceCategoryService.tree()));
    }

    /** GET /api/device-categories —— 平铺列表 */
    @GetMapping
    public ResponseEntity<Result<List<DeviceCategory>>> list() {
        return ResponseEntity.ok(Result.success(deviceCategoryService.listAll()));
    }

    /** GET /api/device-categories/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<DeviceCategory>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceCategoryService.findById(id)));
    }

    /** POST /api/device-categories */
    @Log(title = "设备分类", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<DeviceCategory>> create(@Valid @RequestBody DeviceCategory category) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", deviceCategoryService.create(category)));
    }

    /** PUT /api/device-categories/{id} */
    @Log(title = "设备分类", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<DeviceCategory>> update(@PathVariable Long id,
                                                         @Valid @RequestBody DeviceCategory category) {
        return ResponseEntity.ok(Result.success("修改成功", deviceCategoryService.update(id, category)));
    }

    /** DELETE /api/device-categories/{id} */
    @Log(title = "设备分类", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceCategoryService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
