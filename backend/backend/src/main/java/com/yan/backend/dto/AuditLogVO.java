package com.yan.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计记录（返回给前端 / 导出用）。
 *
 * <p>和实体 {@code AssetAuditLog} 的区别只有一个但很重要：
 * 实体里 {@code changes} 是一段 JSON 文本，这里解成了
 * {@link AuditFieldChange} 列表。让前端去解 JSON 字符串的话，
 * 每个用到审计的地方都要写一遍解析和容错；在服务端解一次、
 * 前端直接拿到结构化的数据更省事，也避免各页面解析口径不一致。
 */
public class AuditLogVO {

    private Long id;

    private String bizType;
    /** 业务类型的中文，如「设备」 */
    private String bizTypeLabel;

    private Long bizId;
    /** 变更时对象名称的快照 */
    private String bizName;
    /** 变更时对象编号的快照 */
    private String bizCode;

    private String action;
    /** 变更动作的中文，如「调拨」 */
    private String actionLabel;

    /** 字段级变更明细。没有字段变化时是空列表（比如删除操作） */
    private List<AuditFieldChange> changes;
    private Integer changeCount;

    private String operator;
    private String operatorName;
    private String remark;
    private LocalDateTime auditTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizTypeLabel() {
        return bizTypeLabel;
    }

    public void setBizTypeLabel(String bizTypeLabel) {
        this.bizTypeLabel = bizTypeLabel;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getBizName() {
        return bizName;
    }

    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    public String getBizCode() {
        return bizCode;
    }

    public void setBizCode(String bizCode) {
        this.bizCode = bizCode;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public List<AuditFieldChange> getChanges() {
        return changes;
    }

    public void setChanges(List<AuditFieldChange> changes) {
        this.changes = changes;
    }

    public Integer getChangeCount() {
        return changeCount;
    }

    public void setChangeCount(Integer changeCount) {
        this.changeCount = changeCount;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }
}
