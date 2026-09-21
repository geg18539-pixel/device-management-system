package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 站内消息（系统通知）。
 *
 * <p>消息是**个人的**：每条都指定一个接收人，只能被接收人看到。
 * 服务层所有按 id 的操作都会校验归属，避免"改个 id 就能读别人的消息"。
 */
@Entity
@Table(name = "sys_message",
        indexes = {
                // 未读数统计和列表查询都是"按接收人 + 已读状态"，加联合索引
                @Index(name = "idx_msg_receiver_read", columnList = "receiver_id,read_flag"),
                @Index(name = "idx_msg_receiver_time", columnList = "receiver_id,create_time")
        },
        uniqueConstraints = {
                // ★ 幂等键。定时任务靠它保证"同一个维保计划同一天只发一条"。
                // MySQL 的 UNIQUE 允许多个 NULL，所以不需要去重键的消息
                //（比如"工单指派"这类一次性事件）留空即可，不会互相冲突
                @UniqueConstraint(name = "uk_msg_biz_key", columnNames = "biz_key")
        })
public class SysMessage {

    // ---------- 消息类型 ----------
    /** 系统通知（管理员手工发的，或其它未归类通知） */
    public static final String TYPE_SYSTEM = "SYSTEM";
    /** 工单被指派给你 */
    public static final String TYPE_REPAIR_ASSIGN = "REPAIR_ASSIGN";
    /** 维保即将到期 / 已逾期 */
    public static final String TYPE_MAINTENANCE_DUE = "MAINTENANCE_DUE";
    /**
     * 配件库存告急（库存 ≤ 预警阈值）。
     *
     * <p><b>和维保提醒的区别是"没有责任人"</b>：维保计划有 maintainer 字段，
     * 所以能精确推给一个人；配件和设备的健康分都没有归属人，
     * 只能按"谁能处理就通知谁"广播（见 SysUserRepository.findAlertRecipients）。
     */
    public static final String TYPE_STOCK_LOW = "STOCK_LOW";
    /** 设备健康分偏低。**每天聚合成一条**，不按设备逐台发 */
    public static final String TYPE_HEALTH_RISK = "HEALTH_RISK";

    // ---------- 消息级别 ----------
    public static final String LEVEL_NORMAL = "普通";
    /** 重要消息在前端会标红，用于"逾期""异常"这类需要立刻处理的 */
    public static final String LEVEL_IMPORTANT = "重要";

    // ---------- 业务类型（决定前端点消息时跳到哪） ----------
    public static final String BIZ_REPAIR = "REPAIR";
    public static final String BIZ_MAINTENANCE_PLAN = "MAINTENANCE_PLAN";
    /** 配件。前端据此跳到「配件耗材」 */
    public static final String BIZ_SPARE_PART = "SPARE_PART";
    /** 单台设备。带 bizId 时前端直接跳到那台设备的档案页 */
    public static final String BIZ_DEVICE = "DEVICE";
    /**
     * 首页看板。
     *
     * <p>**专门给"聚合类"通知用** —— 健康预警一条消息里列了多台设备，
     * 挂到任何一台的档案页都是误导。跳到看板，那里有完整清单，
     * 而且看板上那份清单和这条消息是同一个口径。
     */
    public static final String BIZ_DASHBOARD = "DASHBOARD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收人用户 id */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /**
     * 接收人用户名快照。
     *
     * <p>存快照是为了**用户被删除后消息仍然可读** —— 消息是历史记录，
     * 不该因为账号被清理就变成一条看不出是发给谁的记录。
     */
    @Size(max = 50, message = "接收人不能超过 50 个字符")
    @Column(name = "receiver_name", length = 50)
    private String receiverName;

    /** 消息类型：SYSTEM / REPAIR_ASSIGN / MAINTENANCE_DUE */
    @NotBlank(message = "消息类型不能为空")
    @Column(name = "msg_type", nullable = false, length = 30)
    private String msgType;

    @NotBlank(message = "消息标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 个字符")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "level", nullable = false, length = 20)
    private String level = LEVEL_NORMAL;

    /** 关联业务类型，前端据此决定点击后跳哪个页面 */
    @Size(max = 30, message = "业务类型不能超过 30 个字符")
    @Column(name = "biz_type", length = 30)
    private String bizType;

    /** 关联业务 id（工单 id / 维保计划 id） */
    @Column(name = "biz_id")
    private Long bizId;

    /**
     * 幂等键。**有值时必须全局唯一**。
     *
     * <p>定时任务每跑一次都要判断"这条提醒今天发过没有"。
     * 用"查一下有没有再插"的方式在并发下不可靠（两个实例同时查到都没有 → 都插），
     * 靠数据库的唯一约束来兜底才是可靠的。
     */
    @Size(max = 120, message = "幂等键不能超过 120 个字符")
    @Column(name = "biz_key", length = 120)
    private String bizKey;

    /** 是否已读。允许为空（加列前的老数据），判断一律用 Boolean.TRUE.equals */
    @Column(name = "read_flag")
    private Boolean readFlag = Boolean.FALSE;

    @Column(name = "read_time")
    private LocalDateTime readTime;

    /** 发送人。系统自动发的为空 */
    @Column(name = "sender_id")
    private Long senderId;

    @Size(max = 50, message = "发送人不能超过 50 个字符")
    @Column(name = "sender_name", length = 50)
    private String senderName;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    // ---------- 便捷判断 ----------

    /** 是否已读。null 一律当未读 —— 老数据里可能是 NULL，当未读更安全（宁可多提醒一次） */
    public boolean isRead() {
        return Boolean.TRUE.equals(readFlag);
    }

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public Boolean getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Boolean readFlag) {
        this.readFlag = readFlag;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
