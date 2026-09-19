package com.yan.backend.event;

import com.yan.backend.service.AiFaultAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 工单创建后触发 AI 分析。
 *
 * <p><b>为什么单独一个类，而不是写在 AiFaultAnalysisServiceImpl 里？</b>
 * 因为 @Async 基于 Spring 代理，**同类内部自调用不经过代理，异步不生效**。
 * 如果监听方法和分析方法在同一个类里，事件监听器直接调 analyzeAsync() 就是自调用，
 * 分析会同步跑在事件发布线程上 —— 而那是用户的 HTTP 请求线程，
 * 前端会在 10 秒后超时报错，用户以为报修失败就去重试，结果建出重复工单。
 *
 * <p>拆成独立 bean 之后，调用跨 bean，代理生效，分析真正跑到后台线程上。
 * （和 5.3 的操作日志、AI 对话里的工具执行是同一个坑，这个项目里已经踩到第三次了。）
 */
@Component
public class RepairCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(RepairCreatedListener.class);

    private final AiFaultAnalysisService aiFaultAnalysisService;

    public RepairCreatedListener(AiFaultAnalysisService aiFaultAnalysisService) {
        this.aiFaultAnalysisService = aiFaultAnalysisService;
    }

    /**
     * 在**事务提交之后**才触发分析。
     *
     * <p>用 AFTER_COMMIT 而不是普通 @EventListener，是因为普通监听器在事务**提交前**执行，
     * 那时异步线程去查这条工单可能查不到（读不到别的连接尚未提交的数据），
     * 表现为"AI 分析时有时无地失败"。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairCreated(RepairCreatedEvent event) {
        log.info("收到工单创建事件，准备异步分析：工单#{}, 设备={}",
                event.repairId(), event.deviceName());
        // 交给独立 bean 的 @Async 方法，这里不会阻塞
        aiFaultAnalysisService.analyzeAsync(event.repairId());
    }
}
