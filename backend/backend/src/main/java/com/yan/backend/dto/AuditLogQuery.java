package com.yan.backend.dto;

import java.time.LocalDate;

/**
 * 资产审计的查询条件。
 *
 * <p>维度选取的依据是"审计到底在问什么"：
 * 追查某台设备 → bizType + bizId；追查某个人 → operator；
 * 追查某个时间段发生了什么 → 时间范围；找某类操作 → action；
 * 手上有编号但要找对应记录 → keyword（编号 / 名称模糊）。
 */
public class AuditLogQuery {

    /** DEVICE / PART。null 表示不限 */
    private String bizType;

    /** 只看某一个资产的变更历史。设备详情页的「变更审计」页签用它 */
    private Long bizId;

    /** 变更动作，取 AssetAuditLog.ACTION_* */
    private String action;

    /** 操作人用户名，模糊匹配 */
    private String operator;

    /** 关键词：资产编号 / 名称 / 操作人昵称，模糊匹配 */
    private String keyword;

    /** 审计时间范围，格式 YYYY-MM-DD */
    private LocalDate auditTimeBegin;
    private LocalDate auditTimeEnd;

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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public LocalDate getAuditTimeBegin() {
        return auditTimeBegin;
    }

    public void setAuditTimeBegin(LocalDate auditTimeBegin) {
        this.auditTimeBegin = auditTimeBegin;
    }

    public LocalDate getAuditTimeEnd() {
        return auditTimeEnd;
    }

    public void setAuditTimeEnd(LocalDate auditTimeEnd) {
        this.auditTimeEnd = auditTimeEnd;
    }
}
