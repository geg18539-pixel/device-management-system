package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.entity.Device;
import com.yan.backend.service.DeviceService;
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
 * 设备管理接口。
 *
 * <p>返回值统一包成 Result。外层套 ResponseEntity 是为了同时把 HTTP 状态码也
 * 设对：资源不存在返回 404，参数不合法返回 400，而不是一律 200 再把错误码塞进响应体。
 * 这样做的好处是前端的 fetch/axios 能通过 response.ok 直接识别失败请求。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** GET /api/devices —— 查询全部 */
    @GetMapping
    public ResponseEntity<Result<List<Device>>> list() {
        List<Device> devices = deviceService.findAll();
        return ResponseEntity.ok(Result.success("查询成功", devices));
    }

    /** GET /api/devices/{id} —— 按 id 查询 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<Device>> detail(@PathVariable Long id) {
        Device device = deviceService.findById(id);
        return ResponseEntity.ok(Result.success(device));
    }

    /** POST /api/devices —— 新增 */
    @PostMapping
    public ResponseEntity<Result<Device>> create(@Valid @RequestBody Device device) {
        Device saved = deviceService.save(device);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", saved));
    }

    /** PUT /api/devices/{id} —— 按 id 更新 */
    @PutMapping("/{id}")
    public ResponseEntity<Result<Device>> update(@PathVariable Long id,
                                                 @Valid @RequestBody Device device) {
        Device updated = deviceService.update(id, device);
        return ResponseEntity.ok(Result.success("更新成功", updated));
    }

    /** DELETE /api/devices/{id} —— 按 id 删除 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceService.delete(id);

        // 显式声明为 Result<Void>，让泛型推断出 T = Void
        Result<Void> body = Result.success("删除成功", null);
        return ResponseEntity.ok(body);
    }
}
