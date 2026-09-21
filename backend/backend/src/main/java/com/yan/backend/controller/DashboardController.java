package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.dto.DashboardDigestVO;
import com.yan.backend.dto.DashboardStatsVO;
import com.yan.backend.service.DashboardDigestService;
import com.yan.backend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页数据看板。
 *
 * <p>刻意**不加 @RequirePerm** —— 看板是登录后的默认落地页，
 * admin 和 operator 都要看。这里只依赖 JwtInterceptor 的"必须登录"。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardDigestService digestService;

    public DashboardController(DashboardService dashboardService,
                               DashboardDigestService digestService) {
        this.dashboardService = dashboardService;
        this.digestService = digestService;
    }

    /** GET /api/dashboard/stats —— 指标卡 + 图表 + 待办清单，一次取完 */
    @GetMapping("/stats")
    public ResponseEntity<Result<DashboardStatsVO>> stats() {
        return ResponseEntity.ok(Result.success(dashboardService.stats()));
    }

    /**
     * GET /api/dashboard/digest —— 取当前那份 AI 摘要。
     *
     * <p><b>只读缓存，不会触发生成。</b> 首页每次打开都调它，所以它必须立刻返回；
     * 生成是后台的事（定时任务 / 重启预热 / 下面的刷新接口）。
     *
     * <p>返回体里带着 {@code available} / {@code generating} / {@code message}，
     * 前端据此决定是显示正文、显示"生成中"、还是整块不渲染。
     */
    @GetMapping("/digest")
    public ResponseEntity<Result<DashboardDigestVO>> digest() {
        return ResponseEntity.ok(Result.success(digestService.current()));
    }

    /**
     * POST /api/dashboard/digest/refresh —— 手动触发一次重新生成。
     *
     * <p>两个用途：一是用户觉得数据变了想立刻更新；二是**测试** ——
     * 否则验证这个功能就得把系统时间调到早上 7:30 或干等到第二天，
     * 这和维保提醒那边留 {@code /notify-due} 是同一个理由。
     *
     * <p>**立刻返回**，不等模型跑完。要判断生成完没有，看返回体里的 {@code generating}
     * （前端就是靠它轮询的）。
     *
     * <p>语义上这是"重算一份展示数据"，不是业务写入，所以刻意**不加 @Log** ——
     * 它不改任何业务表，记进操作日志只会给审计添噪音。
     */
    @PostMapping("/digest/refresh")
    public ResponseEntity<Result<DashboardDigestVO>> refreshDigest() {
        boolean started = digestService.requestGenerate();
        DashboardDigestVO vo = digestService.current();

        String message;
        if (started) {
            message = "已开始生成，稍候片刻";
        } else if (vo.isGenerating()) {
            message = "摘要正在生成中";
        } else if (!vo.isEnabled()) {
            message = "首页 AI 摘要已关闭，可在「系统设置」里打开";
        } else {
            // 走到这里只剩"距上次生成不足最小间隔"这一种
            message = "刚刚已经生成过了，请稍后再试";
        }
        return ResponseEntity.ok(Result.success(message, vo));
    }
}
