package com.yan.backend.controller;

import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.LoginLogQuery;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysLoginLog;
import com.yan.backend.service.SysLoginLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志。
 *
 * <p>只读接口：日志只能看，不能改也不能删。
 * 审计日志如果可以被人改，那它作为证据的价值就没了 ——
 * 所以这里**刻意不提供任何写接口**，真的需要清理请直接连数据库。
 */
@RequirePerm("sys:loginlog:list")
@RestController
@RequestMapping("/api/system/login-logs")
public class SysLoginLogController {

    private final SysLoginLogService sysLoginLogService;

    public SysLoginLogController(SysLoginLogService sysLoginLogService) {
        this.sysLoginLogService = sysLoginLogService;
    }

    /**
     * GET /api/system/login-logs —— 分页查询。
     *
     * <p>筛选条件是用户名 / IP / 成功与否 / 时间范围。
     * 排查异常时常用的组合是"只看失败 + 最近一天"。
     */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysLoginLog>>> page(
            LoginLogQuery query,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        return ResponseEntity.ok(Result.success(sysLoginLogService.page(query, pageNum, pageSize)));
    }
}
