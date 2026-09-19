package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.service.DeviceRepairService;
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
import java.util.Map;

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

    // ---------------- 维修日志 ----------------

    /** GET /api/device-repairs/{id}/logs —— 查这张工单的维修日志（时间正序） */
    @GetMapping("/{id}/logs")
    public ResponseEntity<Result<List<DeviceRepairLog>>> logs(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceRepairService.listLogs(id)));
    }

    /**
     * POST /api/device-repairs/{id}/logs —— 追加一条维修记录。
     *
     * <p>日志只增不改也不删：维修记录属于追溯性资料，允许事后改内容就失去意义了。
     * 操作人由后端从登录态取，不接受前端传入，避免伪造"是谁记的这条"。
     */
    @Log(title = "设备维修", businessType = "INSERT")
    @PostMapping("/{id}/logs")
    public ResponseEntity<Result<DeviceRepairLog>> addLog(@PathVariable Long id,
                                                          @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("已记录", deviceRepairService.addLog(id, body.get("content"))));
    }

    // ---------------- AI 分析 ----------------

    /**
     * POST /api/device-repairs/{id}/reanalyze —— 重新触发 AI 分析。
     *
     * <p>接口**立即返回**：真正的分析在后台线程跑，这里只是把任务丢进去。
     * 前端拿到 200 后再看工单的 aiStatus（分析中 → 已完成/失败）。
     * 想等结果的话刷新工单即可。
     */
    @Log(title = "设备维修", businessType = "UPDATE")
    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<Result<Void>> reanalyze(@PathVariable Long id) {
        deviceRepairService.reanalyze(id);
        return ResponseEntity.ok(Result.success("已提交分析任务", null));
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
