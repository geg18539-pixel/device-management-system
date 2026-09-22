package com.yan.backend.controller;

import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.Result;
import com.yan.backend.dto.ConsoleOverviewVO;
import com.yan.backend.service.ConsoleOverviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台首页的系统概览。
 *
 * <p><b>限管理员</b>（{@code @RequireRole("admin")}）—— 和 {@code /api/system/**}
 * 那一批同一个口径。
 *
 * <p>这里面的数字**每一个都只该给管理员看**：登录失败次数会暴露
 * "有谁在撞库"、越权次数会暴露"哪个接口被试探过"、停用账号数属于账号管理范畴。
 * 这和陈批登录日志限 admin 是同一个判断 —— 它是"系统的运行状况"，
 * 不是业务数据。
 *
 * <p>不加 {@code @Log}：它是只读的落地页，每次进来都会调，
 * 记进操作日志只会把审计刷满（同 {@code /dashboard/stats} 的处理）。
 */
@RequireRole("admin")
@RestController
@RequestMapping("/api/system/overview")
public class ConsoleOverviewController {

    private final ConsoleOverviewService consoleOverviewService;

    public ConsoleOverviewController(ConsoleOverviewService consoleOverviewService) {
        this.consoleOverviewService = consoleOverviewService;
    }

    /** GET /api/system/overview —— 后台首页的全部数字，一次取完 */
    @GetMapping
    public ResponseEntity<Result<ConsoleOverviewVO>> overview() {
        return ResponseEntity.ok(Result.success(consoleOverviewService.overview()));
    }
}
