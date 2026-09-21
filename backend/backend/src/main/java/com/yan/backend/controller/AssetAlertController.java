package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.Result;
import com.yan.backend.schedule.AssetAlertNotifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产告警的手动触发。
 *
 * <p>和维保提醒那边的 {@code POST /api/maintenance/notify-due} 是同一个用途：
 * 一是运维发现告警没发出去时手动补一次，二是**测试** ——
 * 否则验证这个功能就得把系统时间调到早上 8:10 或者干等到第二天。
 *
 * <p>单独一个控制器而不是塞进配件或设备控制器：这个动作**同时涉及配件和设备**，
 * 挂在任何一边都会让另一边的人找不到它。
 *
 * <p>限管理员：它是"给全系统发通知"的动作，影响面比一般的业务操作大。
 */
@RestController
@RequestMapping("/api/alerts")
public class AssetAlertController {

    private final AssetAlertNotifier alertNotifier;

    public AssetAlertController(AssetAlertNotifier alertNotifier) {
        this.alertNotifier = alertNotifier;
    }

    /**
     * POST /api/alerts/notify —— 手动跑一遍库存告急 + 设备健康预警。
     *
     * <p>幂等键保证同一天重复触发不会重复发（每人每项每天只有一条）。
     */
    @RequireRole("admin")
    @Log(title = "资产告警", businessType = "OTHER")
    @PostMapping("/notify")
    public ResponseEntity<Result<AssetAlertNotifier.AlertResult>> notifyNow() {
        AssetAlertNotifier.AlertResult r = alertNotifier.notifyAlerts();

        String message;
        if (r.total() == 0) {
            message = "没有需要提醒的项（或今天已经发过了）";
        } else {
            message = "已发出 " + r.total() + " 条（库存 " + r.stockSent()
                    + " 条、健康 " + r.healthSent() + " 条）";
        }
        return ResponseEntity.ok(Result.success(message, r));
    }
}
