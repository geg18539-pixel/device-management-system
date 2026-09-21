package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.AuditActions;
import com.yan.backend.common.DownloadUtils;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AuditLogQuery;
import com.yan.backend.dto.AuditLogVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.excel.AuditLogExcelExporter;
import com.yan.backend.service.AssetAuditService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 资产审计中心。
 *
 * <p><b>只读接口</b>：审计记录只能看，不能改也不能删。
 * 一条能被修改的审计记录是没有证据价值的 —— 所以这里**刻意不提供任何写接口**，
 * 和登录日志、操作日志是同一个立场。
 *
 * <p>接口挂在 {@code /api/system} 下、权限点用 {@code sys:audit:*}，
 * 是按「它要回答的问题」归类的：审计属于系统级的合规能力，
 * 和业务操作（改设备、开工单）不是一类东西。
 * 也正因如此，默认**不授给普通操作员**（见 SystemDataSeeder 的
 * OPERATOR_EXCLUDED_PERMS）—— 和操作日志保持同一个口径。
 */
@RequirePerm("sys:audit:list")
@RestController
@RequestMapping("/api/system/audit-logs")
public class AssetAuditController {

    private final AssetAuditService assetAuditService;
    private final AuditLogExcelExporter excelExporter;

    public AssetAuditController(AssetAuditService assetAuditService,
                                AuditLogExcelExporter excelExporter) {
        this.assetAuditService = assetAuditService;
        this.excelExporter = excelExporter;
    }

    /**
     * GET /api/system/audit-logs —— 分页查询。
     *
     * <p>筛选维度：业务类型 / 某个资产 / 动作 / 操作人 / 关键词 / 时间范围。
     * 追查时常用的组合是"某台设备 + 最近一个月"。
     */
    @GetMapping
    public ResponseEntity<Result<PageResult<AuditLogVO>>> page(
            AuditLogQuery query,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        return ResponseEntity.ok(Result.success(
                assetAuditService.page(query, pageNum, pageSize)));
    }

    /**
     * GET /api/system/audit-logs/actions —— 变更动作字典（代码 → 中文）。
     *
     * <p>给审计中心的「动作」筛选下拉用。放后端返回而不是让前端再抄一份中文，
     * 是为了让"新增一个动作"只需要改一处 —— 两边各存一份的话，
     * 迟早出现下拉里的说法和表格里的说法不一致。
     */
    @GetMapping("/actions")
    public ResponseEntity<Result<Map<String, String>>> actions() {
        return ResponseEntity.ok(Result.success(AuditActions.all()));
    }

    /**
     * GET /api/system/audit-logs/by-device/{deviceId} —— 某台设备的完整变更史。
     *
     * <p>设备详情页的「变更审计」页签用它。单独开一个接口而不是让前端
     * 传 bizType + bizId 走列表接口，是因为这个页签要的是**全部**历史，
     * 不该受分页影响 —— 传分页参数的话，一台改了 200 次的设备
     * 页签里只会显示第一页，看着像"历史丢了"。
     */
    @GetMapping("/by-device/{deviceId}")
    public ResponseEntity<Result<List<AuditLogVO>>> byDevice(@PathVariable Long deviceId) {
        return ResponseEntity.ok(Result.success(
                assetAuditService.listByBiz(AssetAuditLog.BIZ_DEVICE, deviceId)));
    }

    /**
     * GET /api/system/audit-logs/export —— 导出筛选后的全部记录。
     *
     * <p>导出的是**筛选后的全部**而不是当前页 —— 导出的意义就在于拿走完整数据。
     */
    @Log(title = "审计日志导出", businessType = "EXPORT")
    @RequirePerm("sys:audit:export")
    @GetMapping("/export")
    public void export(AuditLogQuery query, HttpServletResponse response) throws IOException {
        List<AuditLogVO> logs = assetAuditService.listForExport(query);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition("资产审计日志_" + LocalDate.now() + ".xlsx", false));

        excelExporter.write(logs, response.getOutputStream());
    }
}
