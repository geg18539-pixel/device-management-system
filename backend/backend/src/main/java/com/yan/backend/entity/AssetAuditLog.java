package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 资产变更审计日志（一次变更动作一行）。
 *
 * <p><b>为什么叫 asset 而不是 device？</b> 它同时给设备和配件记账
 * （配件出入库也是资产变动）。叫 device_audit_log 的话，
 * 里面躺着配件记录会长期误导读代码的人。页面上叫「资产审计中心」，
 * 表名和页面名对齐。
 *
 * <p><b>和 sys_oper_log 的分工（两张表都要，不是重复）</b>
 * <ul>
 *   <li>{@code sys_oper_log} 由 AOP 切面写，回答的是
 *       「<b>谁在什么时候调了哪个接口</b>」——粒度是请求。</li>
 *   <li>本表由 Service 层显式写，回答的是
 *       「<b>哪一条数据的哪个字段从什么变成了什么</b>」——粒度是字段。</li>
 * </ul>
 * 操作日志里只有 URL（{@code PUT /api/devices/42}），**拿不到变更前的值**，
 * 所以回答不了审计最核心的问题："这台设备上个月的状态是什么"。
 * 那是两张表合不到一起的根本原因，不是重复建设。
 *
 * <p><b>为什么快照 bizName / bizCode？</b> 和调拨记录存部门名快照同一个理由：
 * 设备可能被改名甚至被删除。只存 id 的话，历史审计会显示成
 * "设备#42" 甚至查不到。审计资料的职责是反映「当时是什么」，不是「现在是什么」。
 *
 * <p>记录**只增不改不删**。
 */
@Entity
@Table(name = "asset_audit_log", indexes = {
        // 审计中心最常用的两种查询：按某个资产看它的历史、按时间段看全局
        @Index(name = "idx_audit_biz", columnList = "biz_type,biz_id,audit_time"),
        @Index(name = "idx_audit_time", columnList = "audit_time"),
        @Index(name = "idx_audit_operator", columnList = "operator,audit_time")
})
public class AssetAuditLog {

    // ---------- 业务类型 ----------
    /** 设备 */
    public static final String BIZ_DEVICE = "DEVICE";
    /** 配件耗材 */
    public static final String BIZ_PART = "PART";

    // ---------- 变更动作 ----------
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_BORROW = "BORROW";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_REPAIR = "REPAIR";
    public static final String ACTION_TRANSFER = "TRANSFER";
    public static final String ACTION_SCRAP = "SCRAP";
    public static final String ACTION_RESTORE = "RESTORE";
    public static final String ACTION_STOCK_IN = "STOCK_IN";
    public static final String ACTION_STOCK_OUT = "STOCK_OUT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务类型，取 BIZ_* 常量 */
    @Column(name = "biz_type", nullable = false, length = 20)
    private String bizType;

    /** 业务对象 id（设备 id / 配件 id）。对象被删后这个 id 会悬空，属正常 */
    @Column(name = "biz_id", nullable = false)
    private Long bizId;

    /** 变更时对象名称的快照，如「温度传感器 A」 */
    @Column(name = "biz_name", length = 100)
    private String bizName;

    /** 变更时对象编号的快照，如资产编号 ZC-2026-0001、配件编码 PJ-001 */
    @Column(name = "biz_code", length = 50)
    private String bizCode;

    /** 变更动作，取 ACTION_* 常量 */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /**
     * 字段级变更明细，JSON 数组：[{"field":"状态","before":"在线","after":"维修中"}]
     *
     * <p>存 JSON 而不是"一行一个字段"，是因为一次编辑可能改十几个字段，
     * 拆成多行会让审计列表被同一秒的十几条记录刷屏，根本没法扫。
     *
     * <p>用 TEXT 而不是 MySQL 的 JSON 类型：H2（测试用）对 JSON 类型支持有限，
     * 而且这一列只做展示、不做 JSON 路径查询，用不着原生类型。
     */
    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;

    /** 变更了几个字段。列表页直接显示，不用去解析 changes */
    @Column(name = "change_count", nullable = false)
    private Integer changeCount = 0;

    /** 操作人用户名 */
    @Column(name = "operator", length = 50)
    private String operator;

    /** 操作人昵称快照。用户改名或被删后，审计仍显示当时的名字 */
    @Column(name = "operator_name", length = 50)
    private String operatorName;

    /** 备注。调拨原因、报废原因这类"为什么改"的信息 */
    @Column(name = "remark", length = 500)
    private String remark;

    /** 业务发生时间。用 LocalDateTime.now() 而不是依赖审计表自己的 create_time，
     *  这样将来补录历史数据时时间点是可控的 */
    @Column(name = "audit_time", nullable = false)
    private LocalDateTime auditTime;

    // ---------- getter / setter ----------

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

    public String getChanges() {
        return changes;
    }

    public void setChanges(String changes) {
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
