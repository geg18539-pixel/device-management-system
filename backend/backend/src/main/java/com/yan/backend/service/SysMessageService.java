package com.yan.backend.service;

import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysMessage;

import java.util.List;

public interface SysMessageService {

    // ============================================================
    // 发送（给业务代码调用）
    // ============================================================

    /**
     * 给指定用户发一条消息。
     *
     * @param bizKey 幂等键。**有值时全局唯一** —— 定时任务靠它保证同一天不重复发。
     *               一次性事件（工单指派）传 null 即可
     * @return 实际写入返回 true；因为幂等键已存在而跳过返回 false
     */
    boolean send(Long receiverId, String msgType, String title, String content,
                 String level, String bizType, Long bizId, String bizKey);

    /**
     * 按用户名发给某个账号。
     *
     * <p>用于"维修人/负责人"这类只存了名字的业务字段。**解析不到账号时返回 false
     * 并打一条 WARN**，不静默 —— 否则用户会以为通知发了，其实丢了。
     * （外部的临时维修工本来就没有账号，这种情况属于正常，所以只用 WARN 不用 error。）
     */
    boolean sendToUsername(String username, String msgType, String title, String content,
                           String level, String bizType, Long bizId, String bizKey);

    // ============================================================
    // 查询与操作（都是"我自己的消息"）
    // ============================================================

    /** 未读数，给顶栏红点用 */
    long countUnread(Long userId);

    /** 最近的若干条，给顶栏下拉用 */
    List<SysMessage> recent(Long userId, int limit);

    /**
     * 分页查询自己的消息。
     *
     * @param readFlag null 表示不限
     * @param msgType  null 表示不限
     */
    PageResult<SysMessage> page(Long userId, Boolean readFlag, String msgType,
                                int pageNum, int pageSize);

    /** 标记单条已读 */
    SysMessage markRead(Long id, Long userId);

    /** 全部标记已读，返回影响的条数 */
    int markAllRead(Long userId);

    /** 删除单条 */
    void delete(Long id, Long userId);

    /** 清空自己的已读消息，返回删除的条数 */
    int deleteAllRead(Long userId);
}
