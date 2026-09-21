package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 维修日志（工单的进展记录）。
 *
 * <p>一张工单从报修到完工，中间会有多条记录：谁在什么时候发现了什么、
 * 换了什么零件、试过什么方案。原来工单上只有一个 remark 字符串，
 * 装不下这种多条、带时间和操作人的记录，所以单独拆一张表（一对多）。
 *
 * <p>日志是**只增不改**的：追加之后不提供修改和删除接口。
 * 维修记录属于追溯性资料，允许事后改内容就失去意义了。
 */
@Entity
@Table(name = "device_repair_log", indexes = {
        // 日志一定是"按工单查、按时间排"，加个联合索引
        @Index(name = "idx_repair_log_repair", columnList = "repair_id,log_time")
})
public class DeviceRepairLog {

    // ---------- 日志类型常量 ----------
    /** 工单创建时自动写的一条 */
    public static final String TYPE_CREATED = "建单";
    /** 状态流转（开始维修、完工等）自动写 */
    public static final String TYPE_STATUS = "状态变更";
    /** 维修人员手工记录的进展 */
    public static final String TYPE_NOTE = "维修记录";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属工单 id */
    @Column(name = "repair_id", nullable = false)
    private Long repairId;

    /** 日志类型：建单 / 状态变更 / 维修记录 */
    @Column(name = "log_type", nullable = false, length = 20)
    private String logType;

    @NotBlank(message = "日志内容不能为空")
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 操作人。自动生成的日志记录工单报修人/维修人，手工日志记录当前登录用户 */
    @Size(max = 50, message = "操作人不能超过 50 个字符")
    @Column(name = "operator", length = 50)
    private String operator;

    /** 日志时间。手工日志用当前时间，自动日志用对应事件的时间 */
    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRepairId() {
        return repairId;
    }

    public void setRepairId(Long repairId) {
        this.repairId = repairId;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public LocalDateTime getLogTime() {
        return logTime;
    }

    public void setLogTime(LocalDateTime logTime) {
        this.logTime = logTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
