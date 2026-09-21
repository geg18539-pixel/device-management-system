package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.DownloadUtils;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceRepairAssignRequest;
import com.yan.backend.dto.DeviceRepairCloseRequest;
import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.DeviceRepairQuery;
import com.yan.backend.dto.DeviceRepairStatsVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.excel.DeviceRepairExcelExporter;
import com.yan.backend.service.DeviceRepairService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 维修工单。
 *
 * <p>状态流转：**待受理 → 维修中 → 已完成 → 已关闭**，
 * 另有两条捷径：待受理 → 已关闭（误报作废）。
 * 每一步都会写一条状态变更日志，工单的完整经过在维修日志里能一路看下来。
 *
 * <p>这里只有查询和流转 —— 工单的**创建**在设备侧
 * （POST /api/devices/{id}/repair），因为报修要同时改设备状态，
 * 放在设备服务里才能保证两件事在同一个事务里完成。
 */
@RequirePerm("dev:repair:list")
@RestController
@RequestMapping("/api/device-repairs")
public class DeviceRepairController {

    private final DeviceRepairService deviceRepairService;
    private final DeviceRepairExcelExporter excelExporter;

    public DeviceRepairController(DeviceRepairService deviceRepairService,
                                  DeviceRepairExcelExporter excelExporter) {
        this.deviceRepairService = deviceRepairService;
        this.excelExporter = excelExporter;
    }

    // ---------------- 查询 ----------------

    /**
     * GET /api/device-repairs —— 分页 + 条件筛选。
     *
     * <p>条件是 {@link DeviceRepairQuery} 对象，Spring 自动绑定同名 query 参数，
     * 所以 {@code ?repairStatus=待受理&pageNum=1} 这样的调用直接可用。
     *
     * <p>/stats /export 都是字面量段，Spring MVC 里字面量优先于 /{id}，
     * 不会被当成 id 解析。
     */
    @GetMapping
    public ResponseEntity<Result<PageResult<DeviceRepair>>> page(DeviceRepairQuery query) {
        return ResponseEntity.ok(Result.success(deviceRepairService.page(query)));
    }

    /** GET /api/device-repairs/stats —— 工单统计看板的数据 */
    @GetMapping("/stats")
    public ResponseEntity<Result<DeviceRepairStatsVO>> stats() {
        return ResponseEntity.ok(Result.success(deviceRepairService.stats()));
    }

    /** GET /api/device-repairs/export —— 按当前筛选条件导出 Excel（所见即所得） */
    @Log(title = "维修工单导出", businessType = "EXPORT")
    @RequirePerm("dev:repair:export")
    @GetMapping("/export")
    public void export(DeviceRepairQuery query, HttpServletResponse response) throws IOException {
        List<DeviceRepair> repairs = deviceRepairService.listForExport(query);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition("维修工单_" + LocalDate.now() + ".xlsx", false));

        excelExporter.write(repairs, response.getOutputStream());
    }

    /** GET /api/device-repairs/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<DeviceRepair>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceRepairService.findById(id)));
    }

    // ---------------- 状态流转 ----------------

    /** PUT /api/device-repairs/{id}/accept —— 受理，待受理 → 维修中 */
    @Log(title = "设备维修", businessType = "UPDATE")
    @RequirePerm("dev:repair:accept")
    @PutMapping("/{id}/accept")
    public ResponseEntity<Result<DeviceRepair>> accept(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success("工单已受理", deviceRepairService.accept(id)));
    }

    /** PUT /api/device-repairs/{id}/assign —— 指派 / 改派维修人员 */
    @Log(title = "设备维修", businessType = "UPDATE")
    @RequirePerm("dev:repair:assign")
    @PutMapping("/{id}/assign")
    public ResponseEntity<Result<DeviceRepair>> assign(
            @PathVariable Long id, @Valid @RequestBody DeviceRepairAssignRequest request) {
        return ResponseEntity.ok(Result.success("指派成功",
                deviceRepairService.assign(id, request.getRepairer())));
    }

    /**
     * PUT /api/device-repairs/{id}/finish —— 完工，维修中 → 已完成。
     *
     * <p>会同时把设备状态从"维修中"改回"在线"（仅当设备当前确实是维修中），
     * 并写入一条状态变更日志。
     */
    @Log(title = "设备维修", businessType = "UPDATE")
    @RequirePerm("dev:repair:finish")
    @PutMapping("/{id}/finish")
    public ResponseEntity<Result<DeviceRepair>> finish(
            @PathVariable Long id,
            @Valid @RequestBody DeviceRepairFinishRequest request) {

        return ResponseEntity.ok(Result.success("工单已完成", deviceRepairService.finish(id, request)));
    }

    /**
     * PUT /api/device-repairs/{id}/close —— 关闭工单。
     *
     * <p>允许「待受理 → 已关闭」（误报作废）和「已完成 → 已关闭」（归档）。
     * **维修中不能直接关闭**，必须先完工交代处理结果。
     */
    @Log(title = "设备维修", businessType = "UPDATE")
    @RequirePerm("dev:repair:close")
    @PutMapping("/{id}/close")
    public ResponseEntity<Result<DeviceRepair>> close(
            @PathVariable Long id, @Valid @RequestBody DeviceRepairCloseRequest request) {
        return ResponseEntity.ok(Result.success("工单已关闭",
                deviceRepairService.close(id, request.getReason())));
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
    @RequirePerm("dev:repair:log")
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
     */
    @Log(title = "设备维修", businessType = "UPDATE")
    @RequirePerm("dev:repair:analyze")
    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<Result<Void>> reanalyze(@PathVariable Long id) {
        deviceRepairService.reanalyze(id);
        return ResponseEntity.ok(Result.success("已提交分析任务", null));
    }

    /** DELETE /api/device-repairs/{id} —— 只有终态（已完成/已关闭）的工单能删 */
    @Log(title = "设备维修", businessType = "DELETE")
    @RequirePerm("dev:repair:remove")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceRepairService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
