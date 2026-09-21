package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.Result;
import com.yan.backend.entity.SysDictItem;
import com.yan.backend.entity.SysDictType;
import com.yan.backend.service.SysDictService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典管理。
 *
 * <p>分成两组接口，**权限要求完全不同**：
 * <ul>
 *   <li>{@code /api/system/dict/**} —— 管理接口，只给管理员</li>
 *   <li>{@code /api/dict/**} —— 给业务下拉用的只读接口，**只要求登录**。
 *       普通操作员报修时要拿「故障类型」下拉，如果这个也要管理员权限，
 *       功能就直接用不了了。</li>
 * </ul>
 * 两者都只读同一个服务，区别只在权限注解上。
 */
@RestController
public class SysDictController {

    private final SysDictService sysDictService;

    public SysDictController(SysDictService sysDictService) {
        this.sysDictService = sysDictService;
    }

    // ============================================================
    // 给业务用的只读接口（登录即可）
    // ============================================================

    /**
     * GET /api/dict/{dictType} —— 取某个字典启用中的项，给下拉用。
     *
     * <p>只返回 itemValue / itemLabel / sortOrder，不返回备注、创建时间这些
     * 只对管理员有意义的东西。
     */
    @GetMapping("/api/dict/{dictType}")
    public ResponseEntity<Result<List<Map<String, Object>>>> options(
            @PathVariable String dictType) {
        List<Map<String, Object>> options = sysDictService.enabledItems(dictType).stream()
                .map(item -> {
                    Map<String, Object> option = new LinkedHashMap<>();
                    option.put("value", item.getItemValue());
                    option.put("label", item.getItemLabel());
                    return option;
                })
                .toList();
        return ResponseEntity.ok(Result.success(options));
    }

    /**
     * GET /api/dict/batch?types=fault_type,xxx —— 一次取多个字典。
     *
     * <p>页面上一处要用多个字典时，避免发一串请求。
     */
    @GetMapping("/api/dict/batch")
    public ResponseEntity<Result<Map<String, List<Map<String, Object>>>>> batch(
            @RequestParam("types") List<String> types) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String type : types) {
            result.put(type, sysDictService.enabledItems(type).stream()
                    .map(item -> {
                        Map<String, Object> option = new LinkedHashMap<>();
                        option.put("value", item.getItemValue());
                        option.put("label", item.getItemLabel());
                        return option;
                    })
                    .toList());
        }
        return ResponseEntity.ok(Result.success(result));
    }

    // ============================================================
    // 字典类型（管理）
    // ============================================================

    /** GET /api/system/dict/types */
    @RequireRole("admin")
    @RequirePerm("sys:dict:list")
    @GetMapping("/api/system/dict/types")
    public ResponseEntity<Result<List<SysDictType>>> listTypes() {
        return ResponseEntity.ok(Result.success(sysDictService.listTypes()));
    }

    /** POST /api/system/dict/types */
    @RequireRole("admin")
    @RequirePerm("sys:dict:add")
    @Log(title = "字典管理", businessType = "INSERT")
    @PostMapping("/api/system/dict/types")
    public ResponseEntity<Result<SysDictType>> createType(@Valid @RequestBody SysDictType type) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysDictService.createType(type)));
    }

    /** PUT /api/system/dict/types/{id} */
    @RequireRole("admin")
    @RequirePerm("sys:dict:edit")
    @Log(title = "字典管理", businessType = "UPDATE")
    @PutMapping("/api/system/dict/types/{id}")
    public ResponseEntity<Result<SysDictType>> updateType(@PathVariable Long id,
                                                          @Valid @RequestBody SysDictType type) {
        return ResponseEntity.ok(Result.success("修改成功", sysDictService.updateType(id, type)));
    }

    /** DELETE /api/system/dict/types/{id} */
    @RequireRole("admin")
    @RequirePerm("sys:dict:remove")
    @Log(title = "字典管理", businessType = "DELETE")
    @DeleteMapping("/api/system/dict/types/{id}")
    public ResponseEntity<Result<Void>> deleteType(@PathVariable Long id) {
        sysDictService.deleteType(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    // ============================================================
    // 字典项（管理）
    // ============================================================

    /**
     * GET /api/system/dict/items?dictType=fault_type
     *
     * @param onlyEnabled 传 true 只看启用中的，默认看全部（管理页要能改停用的项）
     */
    @RequireRole("admin")
    @RequirePerm("sys:dict:list")
    @GetMapping("/api/system/dict/items")
    public ResponseEntity<Result<List<SysDictItem>>> listItems(
            @RequestParam String dictType,
            @RequestParam(defaultValue = "false") boolean onlyEnabled) {
        return ResponseEntity.ok(Result.success(sysDictService.listItems(dictType, onlyEnabled)));
    }

    /** POST /api/system/dict/items */
    @RequireRole("admin")
    @RequirePerm("sys:dict:add")
    @Log(title = "字典管理", businessType = "INSERT")
    @PostMapping("/api/system/dict/items")
    public ResponseEntity<Result<SysDictItem>> createItem(@Valid @RequestBody SysDictItem item) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sysDictService.createItem(item)));
    }

    /** PUT /api/system/dict/items/{id} */
    @RequireRole("admin")
    @RequirePerm("sys:dict:edit")
    @Log(title = "字典管理", businessType = "UPDATE")
    @PutMapping("/api/system/dict/items/{id}")
    public ResponseEntity<Result<SysDictItem>> updateItem(@PathVariable Long id,
                                                          @Valid @RequestBody SysDictItem item) {
        return ResponseEntity.ok(Result.success("修改成功", sysDictService.updateItem(id, item)));
    }

    /** DELETE /api/system/dict/items/{id} */
    @RequireRole("admin")
    @RequirePerm("sys:dict:remove")
    @Log(title = "字典管理", businessType = "DELETE")
    @DeleteMapping("/api/system/dict/items/{id}")
    public ResponseEntity<Result<Void>> deleteItem(@PathVariable Long id) {
        sysDictService.deleteItem(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
