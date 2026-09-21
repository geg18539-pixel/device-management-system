package com.yan.backend.schedule;

import com.yan.backend.service.DashboardDigestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 首页 AI 摘要的触发点。
 *
 * <p>和 {@link MaintenanceDueNotifier} 是同一套结构（每天定时 + 启动补一次），
 * 但触发时机选得不同：
 *
 * <ul>
 *   <li><b>摘要 7:30</b> —— 管理员上班打开系统第一眼看的。要在他们到岗前就生成好。</li>
 *   <li><b>维保提醒 8:00</b> —— 那是推给具体责任人的消息，晚半小时无所谓，
 *       而且要排在摘要之后，免得两个耗模型/查全表的任务挤在一起。</li>
 * </ul>
 *
 * <p><b>启动预热为什么必须有</b>：摘要只存在内存里（见
 * {@link DashboardDigestService} 的说明），应用一重启就没了。
 * 没有预热的话，重启后的第一个访问者会看到"今日摘要尚未生成"，
 * 得等下一个 7:30 —— 这在开发期几乎是常态。
 */
@Component
public class DashboardDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DashboardDigestScheduler.class);

    private final DashboardDigestService digestService;

    public DashboardDigestScheduler(DashboardDigestService digestService) {
        this.digestService = digestService;
    }

    /**
     * 每天早上 7:30 生成当日摘要。
     *
     * <p>cron 用服务器本地时区（Spring 的 {@code @Scheduled} 默认行为）。
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void scheduledGenerate() {
        try {
            if (digestService.requestGenerate()) {
                log.info("首页 AI 摘要定时任务已触发生成");
            }
        } catch (Exception e) {
            // 定时任务的异常绝不能往上抛：调度线程会把它吞掉并继续下次调度，
            // 堆栈里看不出是哪一次出的问题，所以这里主动记一条
            log.error("首页 AI 摘要定时任务执行失败", e);
        }
    }

    /**
     * 应用启动完成后预热一次。
     *
     * <p>用 {@code ApplicationReadyEvent} 而不是 {@code CommandLineRunner}：
     * 前者在**种子数据跑完之后**才触发，否则刚启动时读到的统计是空的
     * （库里还没数据），生成出来的摘要会是一段"全部为 0"的废话。
     *
     * <p>注意这里**不阻塞启动**：{@code requestGenerate()} 只是把任务丢进线程池，
     * 本方法立刻返回。所以应用不会被一个几十秒的模型调用拖住。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            if (digestService.requestGenerate()) {
                log.info("启动预热：已触发首页 AI 摘要生成（后台进行，不影响启动）");
            }
        } catch (Exception e) {
            // 预热失败绝不能影响启动 —— 它只是首页上的一张卡片
            log.warn("启动预热首页 AI 摘要失败，已跳过：{}", e.getMessage());
        }
    }
}
