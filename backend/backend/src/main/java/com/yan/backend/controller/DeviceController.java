package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * GET /api/devices —— 查全部（不分页）。
     *
     * <p>保留这个接口是为了不破坏已有调用方，也给需要一次性拿全量的场景用。
     * 列表页请用下面的 /page。
     */
    @GetMapping
    public ResponseEntity<Result<List<Device>>> list() {
        return ResponseEntity.ok(Result.success("查询成功", deviceService.findAll()));
    }

    /**
     * GET /api/devices/page —— 分页 + 条件筛选，设备列表页用这个。
     *
     * <p>注意路径顺序：/page 和 /stats 都是字面量，Spring MVC 的路径匹配里
     * 字面量段优先于 /{id} 这样的变量段，所以不会被当成 id 解析。
     */
    @GetMapping("/page")
    public ResponseEntity<Result<PageResult<Device>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        return ResponseEntity.ok(Result.success(
                deviceService.page(pageNum, pageSize, categoryId, status, keyword)));
    }

    /** GET /api/devices/stats —— 图表用的统计数据（状态分布 + 分类统计） */
    @GetMapping("/stats")
    public ResponseEntity<Result<DeviceStatsVO>> stats() {
        return ResponseEntity.ok(Result.success(deviceService.stats()));
    }

    /** GET /api/devices/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<Device>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceService.findById(id)));
    }

    /** POST /api/devices */
    @Log(title = "设备管理", businessType = "INSERT")
    @PostMapping
    public ResponseEntity<Result<Device>> create(@Valid @RequestBody Device device) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", deviceService.save(device)));
    }

    /** PUT /api/devices/{id} */
    @Log(title = "设备管理", businessType = "UPDATE")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Device>> update(@PathVariable Long id,
                                                 @Valid @RequestBody Device device) {
        return ResponseEntity.ok(Result.success("修改成功", deviceService.update(id, device)));
    }

    /** POST /api/devices/{id}/borrow —— 借用，状态改为"使用中" */
    @Log(title = "设备借用", businessType = "UPDATE")
    @PostMapping("/{id}/borrow")
    public ResponseEntity<Result<Device>> borrow(@PathVariable Long id,
                                                 @Valid @RequestBody DeviceBorrowRequest request) {
        return ResponseEntity.ok(Result.success("借用成功", deviceService.borrow(id, request)));
    }

    /** POST /api/devices/{id}/return —— 归还，状态改回"在线" */
    @Log(title = "设备借用", businessType = "UPDATE")
    @PostMapping("/{id}/return")
    public ResponseEntity<Result<Device>> giveBack(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success("归还成功", deviceService.giveBack(id)));
    }

    /**
     * POST /api/devices/{id}/repair —— 报修。
     *
     * <p>会同时生成一张维修工单并把设备状态改成"维修中"，
     * 工单完工时（PUT /api/device-repairs/{id}/finish）再把状态改回"在线"。
     */
    @Log(title = "设备维修", businessType = "UPDATE")
    @PostMapping("/{id}/repair")
    public ResponseEntity<Result<Device>> reportRepair(@PathVariable Long id,
                                                       @Valid @RequestBody DeviceRepairRequest request) {
        return ResponseEntity.ok(Result.success("报修成功", deviceService.reportRepair(id, request)));
    }

    /** DELETE /api/devices/{id} */
    @Log(title = "设备管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
