package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.ConfigKeys;
import com.yan.backend.common.Result;
import com.yan.backend.dto.MaintenanceExecuteRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceMaintenanceRecord;
import com.yan.backend.schedule.MaintenanceDueNotifier;
import com.yan.backend.service.DeviceMaintenanceService;
import com.yan.backend.service.SysConfigService;
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

/**
 * 设备维保管理。
 *
 * <p>和维修工单的区别：工单是"设备坏了才报修"（被动），
 * 维保是"按计划定期保养"（主动、预防性）。
 */
@RequirePerm("dev:maint:list")
@RestController
@RequestMapping("/api/maintenance")
public class DeviceMaintenanceController {

    private final DeviceMaintenanceService maintenanceService;
    private final SysConfigService configService;
    private final MaintenanceDueNotifier maintenanceDueNotifier;

    public DeviceMaintenanceController(DeviceMaintenanceService maintenanceService,
                                       SysConfigService configService,
                                       MaintenanceDueNotifier maintenanceDueNotifier) {
        this.maintenanceService = maintenanceService;
        this.configService = configService;
        this.maintenanceDueNotifier = maintenanceDueNotifier;
    }

    /**
     * 提前多少天算"即将到期"。
     *
     * <p>从**系统参数**读，管理员在「系统设置」里改完即时生效。
     * 不同企业对"快到期了"的容忍度差别很大（精密设备可能要求提前 60 天备件），
     * 所以做成可配的。库里没配就回退 yml 默认值。
     */
    private int warnDays() {
        return configService.getInt(
                ConfigKeys.MAINTENANCE_WARN_DAYS, ConfigKeys.MAINTENANCE_WARN_DAYS_DEFAULT);
    }

    // ---------------- 计划 ----------------

    /** GET /api/maintenance/plans —— 计划分页（默认按到期日从近到远） */
    @GetMapping("/plans")
    public ResponseEntity<Result<PageResult<DeviceMaintenancePlan>>> pagePlans(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(
                maintenanceService.pagePlans(status, keyword, pageNum, pageSize)));
    }

    /** GET /api/maintenance/plans/due —— 即将到期 / 已逾期的计划 */
    @GetMapping("/plans/due")
    public ResponseEntity<Result<List<DeviceMaintenancePlan>>> duePlans() {
        return ResponseEntity.ok(Result.success(maintenanceService.listDue(warnDays())));
    }

    /** GET /api/maintenance/devices/{deviceId}/plans —— 某台设备的计划（详情页用） */
    @GetMapping("/devices/{deviceId}/plans")
    public ResponseEntity<Result<List<DeviceMaintenancePlan>>> plansByDevice(
            @PathVariable Long deviceId) {
        return ResponseEntity.ok(Result.success(maintenanceService.listPlansByDevice(deviceId)));
    }

    /** GET /api/maintenance/plans/{id} */
    @GetMapping("/plans/{id}")
    public ResponseEntity<Result<DeviceMaintenancePlan>> planDetail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(maintenanceService.findPlan(id)));
    }

    /** POST /api/maintenance/plans —— 新建计划 */
    @Log(title = "维保计划", businessType = "INSERT")
    @RequirePerm("dev:maint:add")
    @PostMapping("/plans")
    public ResponseEntity<Result<DeviceMaintenancePlan>> createPlan(
            @Valid @RequestBody DeviceMaintenancePlan plan) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", maintenanceService.createPlan(plan)));
    }

    /** PUT /api/maintenance/plans/{id} —— 修改计划 */
    @Log(title = "维保计划", businessType = "UPDATE")
    @RequirePerm("dev:maint:edit")
    @PutMapping("/plans/{id}")
    public ResponseEntity<Result<DeviceMaintenancePlan>> updatePlan(
            @PathVariable Long id, @Valid @RequestBody DeviceMaintenancePlan plan) {
        return ResponseEntity.ok(Result.success("修改成功", maintenanceService.updatePlan(id, plan)));
    }

    /** DELETE /api/maintenance/plans/{id} */
    @Log(title = "维保计划", businessType = "DELETE")
    @RequirePerm("dev:maint:remove")
    @DeleteMapping("/plans/{id}")
    public ResponseEntity<Result<Void>> deletePlan(@PathVariable Long id) {
        maintenanceService.deletePlan(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    // ---------------- 执行维保 ----------------

    /**
     * POST /api/maintenance/plans/{planId}/execute —— 按计划执行一次维保。
     *
     * <p>会写一条维保记录，并把计划的下次到期日往后推一个周期。
     */
    @Log(title = "设备维保", businessType = "INSERT")
    @RequirePerm("dev:maint:execute")
    @PostMapping("/plans/{planId}/execute")
    public ResponseEntity<Result<DeviceMaintenanceRecord>> executeByPlan(
            @PathVariable Long planId,
            @Valid @RequestBody MaintenanceExecuteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("维保已记录",
                        maintenanceService.execute(planId, null, request)));
    }

    /**
     * POST /api/devices/{deviceId}/maintenance-records —— 临时保养（不挂计划）。
     *
     * <p>有些保养是被动的（巡检时顺手做了），或者设备还没建计划。
     * 这类只写记录，不动任何计划。
     */
    @Log(title = "设备维保", businessType = "INSERT")
    @RequirePerm("dev:maint:execute")
    @PostMapping("/devices/{deviceId}/records")
    public ResponseEntity<Result<DeviceMaintenanceRecord>> executeByDevice(
            @PathVariable Long deviceId,
            @Valid @RequestBody MaintenanceExecuteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("维保已记录",
                        maintenanceService.execute(null, deviceId, request)));
    }

    // ---------------- 记录 ----------------

    /** GET /api/maintenance/records —— 维保记录分页（deviceId 可选） */
    @GetMapping("/records")
    public ResponseEntity<Result<PageResult<DeviceMaintenanceRecord>>> pageRecords(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(
                maintenanceService.pageRecords(deviceId, pageNum, pageSize)));
    }

    /**
     * POST /api/maintenance/notify-due —— 手动触发一次到期提醒的扫描与发送。
     *
     * <p>两个用途：一是运维发现提醒没发出去时手动补一次；二是**测试**——
     * 否则验证这个功能就得把系统时间调到早上 8 点，或者干等到第二天。
     *
     * <p>幂等键保证同一天重复触发不会重复发。
     */
    @RequirePerm("dev:maint:edit")
    @Log(title = "维保提醒", businessType = "OTHER")
    @PostMapping("/notify-due")
    public ResponseEntity<Result<Integer>> notifyDue() {
        int sent = maintenanceDueNotifier.notifyDueMaintenance();
        return ResponseEntity.ok(Result.success(
                sent > 0 ? "已发出 " + sent + " 条提醒" : "没有需要提醒的计划（或今天已经发过了）",
                sent));
    }
}
