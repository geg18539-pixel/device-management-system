package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.DownloadUtils;
import com.yan.backend.common.Result;
import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceHealthVO;
import com.yan.backend.dto.DeviceImportResultVO;
import com.yan.backend.dto.DeviceLedgerVO;
import com.yan.backend.dto.DeviceProfileVO;
import com.yan.backend.dto.DeviceQuery;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceScrapRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.DeviceTransferRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceTransfer;
import com.yan.backend.excel.DeviceExcelExporter;
import com.yan.backend.service.DeviceHealthService;
import com.yan.backend.service.DeviceProfileService;
import com.yan.backend.service.DeviceImportService;
import com.yan.backend.service.DeviceService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 设备管理。
 *
 * <p>类上的 {@code @RequirePerm("dev:device:list")} 是**默认**：所有查询接口
 * 继承它。写操作各自在方法上标注更细的权限点（方法级优先于类级）。
 *
 * <p>这样分层的好处：新加一个查询接口不用记得加注解（默认就是"能看设备"），
 * 只有加写接口时才需要显式决定"这该归到哪个权限点"。
 */
@RequirePerm("dev:device:list")
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceProfileService deviceProfileService;
    private final DeviceImportService importService;
    private final DeviceExcelExporter excelExporter;
    private final DeviceHealthService deviceHealthService;

    public DeviceController(DeviceService deviceService,
                            DeviceProfileService deviceProfileService,
                            DeviceImportService importService,
                            DeviceExcelExporter excelExporter,
                            DeviceHealthService deviceHealthService) {
        this.deviceService = deviceService;
        this.deviceProfileService = deviceProfileService;
        this.importService = importService;
        this.excelExporter = excelExporter;
        this.deviceHealthService = deviceHealthService;
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
     * <p>条件是 {@link DeviceQuery} 对象，Spring 会自动把 URL 上的同名 query 参数
     * 绑到它的字段上 —— 所以 {@code ?deptId=3&pageNum=1} 这样的调用照常可用。
     *
     * <p>路径顺序：/page /stats /ledger /export 都是字面量段，
     * Spring MVC 里字面量优先于 /{id} 这样的变量段，不会被当成 id 解析。
     */
    @GetMapping("/page")
    public ResponseEntity<Result<PageResult<Device>>> page(DeviceQuery query) {
        return ResponseEntity.ok(Result.success(deviceService.page(query)));
    }

    /**
     * GET /api/devices/{id}/health —— 某台设备的健康分。
     *
     * <p>设备详情页用。和看板上那张"健康风险"卡取的是**同一套算法**
     * （{@code DeviceHealthService}），所以同一台设备在两处显示的分数
     * 必然一致 —— 各算各的话，看板说 62、详情页说 70，谁都不知道信哪个。
     */
    @GetMapping("/{id}/health")
    public ResponseEntity<Result<DeviceHealthVO>> health(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceHealthService.evaluate(id)));
    }

    /**
     * GET /api/devices/export —— 按当前筛选条件导出 Excel（所见即所得）。
     *
     * <p>导出的是**筛选后的全部**，不是当前页 —— 导出的意义就在于拿走完整数据。
     */
    @Log(title = "设备导出", businessType = "EXPORT")
    @RequirePerm("dev:device:export")
    @GetMapping("/export")
    public void export(DeviceQuery query, HttpServletResponse response) throws IOException {
        List<Device> devices = deviceService.listForExport(query);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition("设备列表_" + LocalDate.now() + ".xlsx", false));

        excelExporter.writeDevices(devices, response.getOutputStream());
    }

    // ---------------- 设备台账 ----------------

    /** GET /api/devices/ledger —— 台账汇总（按部门 / 按分类 / 按生命周期状态） */
    @RequirePerm("dev:ledger:list")
    @GetMapping("/ledger")
    public ResponseEntity<Result<DeviceLedgerVO>> ledger() {
        return ResponseEntity.ok(Result.success(deviceService.ledger()));
    }

    /**
     * GET /api/devices/ledger/export —— 导出完整台账。
     *
     * <p>一个工作簿三个 sheet：台账明细、按部门统计、按分类统计。
     * 明细同样受查询条件影响，所以台账页也能"筛完再导"。
     */
    @Log(title = "设备台账导出", businessType = "EXPORT")
    @RequirePerm("dev:ledger:export")
    @GetMapping("/ledger/export")
    public void exportLedger(DeviceQuery query, HttpServletResponse response) throws IOException {
        List<Device> devices = deviceService.listForExport(query);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition("设备台账_" + LocalDate.now() + ".xlsx", false));

        excelExporter.writeLedger(devices, deviceService.ledger(), response.getOutputStream());
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

    /**
     * GET /api/devices/{id}/profile —— 设备档案详情。
     *
     * <p>一次返回：基础信息 + 维保计划 + 维保记录 + 维修工单历史 +
     * 配件更换记录 + 附件 + 调拨记录。设备详情页只发这一个请求。
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<Result<DeviceProfileVO>> profile(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceProfileService.profile(id)));
    }

    /** POST /api/devices */
    @Log(title = "设备管理", businessType = "INSERT")
    @RequirePerm("dev:device:add")
    @PostMapping
    public ResponseEntity<Result<Device>> create(@Valid @RequestBody Device device) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", deviceService.save(device)));
    }

    /** PUT /api/devices/{id} */
    @Log(title = "设备管理", businessType = "UPDATE")
    @RequirePerm("dev:device:edit")
    @PutMapping("/{id}")
    public ResponseEntity<Result<Device>> update(@PathVariable Long id,
                                                 @Valid @RequestBody Device device) {
        return ResponseEntity.ok(Result.success("修改成功", deviceService.update(id, device)));
    }

    /** POST /api/devices/{id}/borrow —— 借用，状态改为"使用中" */
    @Log(title = "设备借用", businessType = "UPDATE")
    @RequirePerm("dev:device:borrow")
    @PostMapping("/{id}/borrow")
    public ResponseEntity<Result<Device>> borrow(@PathVariable Long id,
                                                 @Valid @RequestBody DeviceBorrowRequest request) {
        return ResponseEntity.ok(Result.success("借用成功", deviceService.borrow(id, request)));
    }

    /** POST /api/devices/{id}/return —— 归还，状态改回"在线" */
    @Log(title = "设备借用", businessType = "UPDATE")
    @RequirePerm("dev:device:return")
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
    @RequirePerm("dev:device:repair")
    @PostMapping("/{id}/repair")
    public ResponseEntity<Result<Device>> reportRepair(@PathVariable Long id,
                                                       @Valid @RequestBody DeviceRepairRequest request) {
        return ResponseEntity.ok(Result.success("报修成功", deviceService.reportRepair(id, request)));
    }

    // ---------------- 批量导入 ----------------

    /**
     * GET /api/devices/import-template —— 下载导入模板。
     *
     * <p>模板里带一行示例数据和一个「填写说明」页（含系统里现有的分类和部门名称），
     * 用户照着填就不会出现"名称不存在"这类错误。
     */
    @RequirePerm("dev:device:import")
    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition("设备导入模板.xlsx", false));
        importService.writeTemplate(response.getOutputStream());
    }

    /**
     * POST /api/devices/import —— 批量导入（multipart，字段名 file）。
     *
     * <p><b>逐行处理、部分成功</b>：能进的行照常入库，不能进的行跳过并在结果里
     * 返回"第几行、哪台设备、为什么"。前端把失败明细渲染成表格，
     * 用户改完那几行可以重新导入一次（已成功的行会因为编号重复被跳过，不会重复入库）。
     */
    @Log(title = "设备导入", businessType = "IMPORT")
    @RequirePerm("dev:device:import")
    @PostMapping("/import")
    public ResponseEntity<Result<DeviceImportResultVO>> importDevices(
            @RequestParam("file") MultipartFile file) {
        DeviceImportResultVO result = importService.importDevices(file);

        String message = result.getFailCount() == 0
                ? "全部导入成功，共 " + result.getSuccessCount() + " 条"
                : "导入完成：成功 " + result.getSuccessCount()
                        + " 条，失败 " + result.getFailCount() + " 条";
        return ResponseEntity.ok(Result.success(message, result));
    }

    /** DELETE /api/devices/{id} */
    @Log(title = "设备管理", businessType = "DELETE")
    @RequirePerm("dev:device:remove")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    // ---------------- 调拨 / 报废 ----------------

    /** GET /api/devices/{id}/transfers —— 该设备的调拨历史 */
    @GetMapping("/{id}/transfers")
    public ResponseEntity<Result<List<DeviceTransfer>>> transfers(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(deviceService.findTransfers(id)));
    }

    /**
     * POST /api/devices/{id}/transfer —— 调拨。
     *
     * <p>设备归属部门的变更**只能走这里**，编辑接口不处理 deptId，
     * 保证每一次部门变更都留下记录。
     */
    @Log(title = "设备调拨", businessType = "UPDATE")
    @RequirePerm("dev:device:transfer")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Result<Device>> transfer(@PathVariable Long id,
                                                   @Valid @RequestBody DeviceTransferRequest request) {
        return ResponseEntity.ok(Result.success("调拨成功", deviceService.transfer(id, request)));
    }

    /**
     * POST /api/devices/{id}/scrap —— 报废。
     *
     * <p>会把生命周期状态改成"报废"并记录报废日期/原因/操作人。
     */
    @Log(title = "设备报废", businessType = "UPDATE")
    @RequirePerm("dev:device:scrap")
    @PostMapping("/{id}/scrap")
    public ResponseEntity<Result<Device>> scrap(@PathVariable Long id,
                                                @Valid @RequestBody DeviceScrapRequest request) {
        return ResponseEntity.ok(Result.success("报废成功", deviceService.scrap(id, request)));
    }

    /**
     * POST /api/devices/{id}/restore —— 取消报废（误操作的补救）。
     *
     * <p>权限用「设备报废」：能决定报废的人才能决定撤销报废，
     * 单开一个权限点没有意义。
     */
    @Log(title = "设备报废", businessType = "UPDATE")
    @RequirePerm("dev:device:scrap")
    @PostMapping("/{id}/restore")
    public ResponseEntity<Result<Device>> restore(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success("已恢复为正常状态", deviceService.restore(id)));
    }
}
