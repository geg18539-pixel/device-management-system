package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.service.DeviceRepairService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 维修工单。
 *
 * <p>这里只有查询和完工 —— 工单的**创建**在设备侧
 * （POST /api/devices/{id}/repair），因为报修要同时改设备状态，
 * 放在设备服务里才能保证两件事在同一个事务里完成。
 */
@RestController
@RequestMapping("/api/device-repairs")
public class DeviceRepairController {

    private final DeviceRepairService deviceRepairService;

    public DeviceRepairController(DeviceRepairService deviceRepairService) {
        this.deviceRepairService = deviceRepairService;
    }

    /** GET /api/device-repairs?pageNum=1&pageSize=10&repairStatus=待维修&deviceId=1 */
    @GetMapping
    public ResponseEntity<Result<PageResult<DeviceRepair>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String repairStatus,
            @RequestParam(required = false) Long deviceId) {

        return ResponseEntity.ok(Result.success(
                deviceRepairService.page(pageNum, pageSize, repairStatus, deviceId)));
    }

    /** GET /api/device-repairs/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<DeviceRepair>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceRepairService.findById(id)));
    }

    /** PUT /api/device-repairs/{id}/finish —— 完工，同时把设备状态改回"在线" */
    @Log(title = "设备维修", businessType = "UPDATE")
    @PutMapping("/{id}/finish")
    public ResponseEntity<Result<DeviceRepair>> finish(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRepairFinishRequest request) {

        return ResponseEntity.ok(Result.success("工单已完成", deviceRepairService.finish(id, request)));
    }

    /** DELETE /api/device-repairs/{id} */
    @Log(title = "设备维修", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceRepairService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
