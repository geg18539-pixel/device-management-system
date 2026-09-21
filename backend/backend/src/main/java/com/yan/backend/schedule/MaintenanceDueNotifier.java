package com.yan.backend.schedule;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.SysMessage;
import com.yan.backend.service.DeviceMaintenanceService;
import com.yan.backend.service.SysConfigService;
import com.yan.backend.service.SysMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 维保到期的站内提醒。
 *
 * <p>每天定时扫描"即将到期 / 已逾期"的维保计划，给计划的负责人发一条站内消息。
 *
 * <p><b>两个触发点：</b>
 * <ol>
 *   <li>每天定时（默认早上 8 点）—— 正常路径</li>
 *   <li><b>应用启动时补扫一次</b> —— 覆盖"应用关了好几天没开"的情况。
 *       如果只在定时点发，那几天正好没开机的提醒就永远丢了</li>
 * </ol>
 *
 * <p><b>幂等靠 bizKey 唯一约束</b>（计划 id + 日期）。不用"先查再插"是因为
 * 并发下不可靠：两个实例同时查到"今天没发过"就会各发一条。
 * 数据库的唯一约束才是真正的兜底。
 */
@Component
public class MaintenanceDueNotifier {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceDueNotifier.class);

    /** 幂等键前缀，和别的消息类型区分开 */
    private static final String BIZ_KEY_PREFIX = "maint_due:";

    private final DeviceMaintenanceService maintenanceService;
    private final SysMessageService messageService;
    private final SysConfigService configService;

    public MaintenanceDueNotifier(DeviceMaintenanceService maintenanceService,
                                  SysMessageService messageService,
                                  SysConfigService configService) {
        this.maintenanceService = maintenanceService;
        this.messageService = messageService;
        this.configService = configService;
    }

    /**
     * 每天早上 8:00 扫描一次。
     *
     * <p>选 8 点而不是凌晨：消息是发给人看的，凌晨发出去也是躺在那里，
     * 早上上班打开系统看到正好。而且这时候数据库压力最小。
     *
     * <p>cron 用**服务器本地时区**（Spring 的 @Scheduled 默认行为）。
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void scheduledNotify() {
        try {
            notifyDueMaintenance();
        } catch (Exception e) {
            // 定时任务的异常绝不能往上抛：Spring 的调度线程会把它吞掉并继续下次调度，
            // 但堆栈里看不出是哪次任务出的问题，所以这里主动记一条
            log.error("维保到期提醒定时任务执行失败", e);
        }
    }

    /**
     * 应用启动完成时补扫一次。
     *
     * <p>用 {@code ApplicationReadyEvent} 而不是 {@code CommandLineRunner}：
     * 前者在**种子数据跑完之后**才触发（CommandLineRunner 更早），
     * 否则启动时还没读到配置项和维保计划，扫出来是空的。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int sent = notifyDueMaintenance();
            if (sent > 0) {
                log.info("启动补扫：已发出 {} 条维保到期提醒", sent);
            }
        } catch (Exception e) {
            // 启动补扫失败绝不能影响应用启动 —— 它只是个通知
            log.warn("启动时补扫维保到期提醒失败，已跳过：{}", e.getMessage());
        }
    }

    /**
     * 执行一次扫描与发送。
     *
     * @return 实际发出的条数（因为幂等被跳过的、找不到负责人的都不计入）
     */
    public int notifyDueMaintenance() {
        if (!configService.getBoolean(ConfigKeys.MAINTENANCE_NOTIFY_ENABLED,
                ConfigKeys.MAINTENANCE_NOTIFY_ENABLED_DEFAULT)) {
            log.info("维保到期提醒已关闭（系统参数 {}），跳过",
                    ConfigKeys.MAINTENANCE_NOTIFY_ENABLED);
            return 0;
        }

        int warnDays = configService.getInt(
                ConfigKeys.MAINTENANCE_WARN_DAYS, ConfigKeys.MAINTENANCE_WARN_DAYS_DEFAULT);

        // 复用维保 Service 的口径：它已经排除了已报废设备，
        // 也只看"启用"状态的计划
        List<DeviceMaintenancePlan> due = maintenanceService.listDue(warnDays);
        if (due.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int sent = 0;

        for (DeviceMaintenancePlan plan : due) {
            if (!StringUtils.hasText(plan.getMaintainer())) {
                // 计划没填负责人就没人可通知。不报错 —— 责任人字段本来就是选填的
                continue;
            }
            if (sendOne(plan, today)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendOne(DeviceMaintenancePlan plan, LocalDate today) {
        LocalDate next = plan.getNextMaintenanceDate();
        long days = next == null ? 0 : ChronoUnit.DAYS.between(today, next);
        boolean overdue = days < 0;

        String title = (overdue ? "维保已逾期：" : "维保即将到期：")
                + (StringUtils.hasText(plan.getDeviceName()) ? plan.getDeviceName() : "设备");

        StringBuilder content = new StringBuilder();
        content.append("计划：").append(plan.getPlanName());
        if (StringUtils.hasText(plan.getDeviceName())) {
            content.append("｜设备：").append(plan.getDeviceName());
        }
        if (next != null) {
            content.append("｜到期日：").append(next);
            content.append("｜").append(days < 0 ? "已逾期 " + (-days) + " 天"
                    : days == 0 ? "今天到期" : "还剩 " + days + " 天");
        }
        content.append("\n请安排保养并在「维保管理」里登记结果。");

        // 幂等键：同一个计划同一天只发一条。
        // 定时任务一天可能跑多次（手动触发 + 定时撞上），没有它就会重复轰炸
        String bizKey = BIZ_KEY_PREFIX + plan.getId() + ":" + today;

        return messageService.sendToUsername(
                plan.getMaintainer(),
                SysMessage.TYPE_MAINTENANCE_DUE,
                title,
                content.toString(),
                // 逾期的标成重要，前端会标红
                overdue ? SysMessage.LEVEL_IMPORTANT : SysMessage.LEVEL_NORMAL,
                SysMessage.BIZ_MAINTENANCE_PLAN,
                plan.getId(),
                bizKey);
    }
}
