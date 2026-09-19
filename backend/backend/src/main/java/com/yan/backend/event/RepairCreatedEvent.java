package com.yan.backend.event;

/**
 * 工单创建完成事件。
 *
 * <p>用事件而不是直接在创建工单的方法里调 AI 分析，是为了让分析发生在
 * **事务提交之后**：如果直接调用，异步线程可能在主事务还没提交时就去查这条工单，
 * 结果查不到（读不到未提交的数据），表现为"分析随机失败"。
 * 配合 @TransactionalEventListener(phase = AFTER_COMMIT) 就能保证顺序正确。
 */
public record RepairCreatedEvent(Long repairId, String deviceName, String faultDesc) {
}
