package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备维修工单。
 *
 * <p>一张工单对应一次报修到维修完成的全过程。设备报修时创建工单（状态"待受理"），
 * 同时把设备状态改成"维修中"；工单完工时把设备状态改回"在线"。
 * 这个联动放在 Service 里做，保证两边状态一致。
 *
 * <p>状态流转：待受理 → 维修中 → 已完成 → 已关闭，
 * 另有待受理 → 已关闭（误报作废）这条捷径。
 */
@Entity
@Table(name = "device_repair")
public class DeviceRepair {

    // ============================================================
    // 工单状态常量
    //
    // 流转：待受理 → 维修中 → 已完成 → 已关闭
    //         └──────────────┴──→ 已关闭（作废）
    // ============================================================

    /** 待受理。刚报修、还没人接单 */
    public static final String STATUS_PENDING = "待受理";

    /**
     * ★ 旧版本的「待受理」值。
     *
     * <p>企业级改造之前，报修建单写的是「待维修」。库里的历史工单仍然是这个值，
     * 而**我们没有做批量数据迁移**（用户明确选择"兼容映射"而不是改库）。
     *
     * <p>所以约定是：**新数据一律写 {@link #STATUS_PENDING}，
     * 旧值只在判断和展示时按同一语义处理**。
     * 任何比较状态的地方都要先过 {@link #normalizeStatus}，不能直接 equals。
     */
    public static final String STATUS_PENDING_LEGACY = "待维修";

    /** 已受理、正在维修 */
    public static final String STATUS_REPAIRING = "维修中";
    /** 维修完成 */
    public static final String STATUS_FINISHED = "已完成";
    /** 已关闭。终态，不再流转 */
    public static final String STATUS_CLOSED = "已关闭";

    /**
     * 把旧状态值归一到当前语义。
     *
     * <p>所有"这个工单是不是待受理"的判断都必须走这里。
     * 直接写 {@code STATUS_PENDING.equals(status)} 会让历史工单被漏判 ——
     * 表现为"列表显示待受理，但点受理按钮提示状态不对"这种自相矛盾的现象。
     */
    public static String normalizeStatus(String status) {
        return STATUS_PENDING_LEGACY.equals(status) ? STATUS_PENDING : status;
    }

    /** 是否处于「待受理」（含旧值「待维修」） */
    public static boolean isPending(String status) {
        return STATUS_PENDING.equals(normalizeStatus(status));
    }

    /**
     * 把一个状态筛选条件**扩展成库里可能出现的所有等价值**。
     *
     * <p>因为旧值「待维修」和新值「待受理」在库里同时存在，按状态筛选时
     * 不能做等值匹配 —— 否则筛「待受理」会把全部历史工单漏掉，
     * 用户看到的现象是"一点这个筛选项历史记录就全没了"，非常像数据丢失。
     *
     * <p>收在这里而不是各处自己写，是因为有三处都要用（列表筛选、AI 工具查询、
     * 统计），漏掉任何一处都会造成"同一份数据在不同页面数量对不上"。
     */
    public static List<String> expandStatusFilter(String status) {
        String normalized = normalizeStatus(status);
        if (STATUS_PENDING.equals(normalized)) {
            return List.of(STATUS_PENDING, STATUS_PENDING_LEGACY);
        }
        return List.of(normalized);
    }

    /**
     * 是否已到终态（已完成 / 已关闭）。
     *
     * <p>统计"待处理工单"用的就是这个口径：**排除终态**而不是"等于某几个进行中状态"，
     * 这样以后再加中间态（已派单、待配件…）不用回来改统计逻辑。
     */
    public static boolean isTerminal(String status) {
        return STATUS_FINISHED.equals(status) || STATUS_CLOSED.equals(status);
    }

    // ---------- AI 分析状态常量 ----------
    /** 刚建单，还没分析 */
    public static final String AI_PENDING = "待分析";
    /** 正在调用模型 */
    public static final String AI_RUNNING = "分析中";
    public static final String AI_DONE = "已完成";
    /** 分析失败（模型没启动、超时、返回内容无法解析等）。失败不影响工单本身的使用 */
    public static final String AI_FAILED = "失败";

    // ---------- 严重程度常量 ----------
    public static final String SEVERITY_HIGH = "高";
    public static final String SEVERITY_MEDIUM = "中";
    public static final String SEVERITY_LOW = "低";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的设备 id */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 报修时设备的名称**快照**。
     *
     * <p>有意做冗余：工单是历史记录，应该反映"当时"设备叫什么。
     * 设备后来改名（甚至删除）不应该影响历史工单的可读性，
     * 而且列表页展示时也不用再 join 一次设备表。
     */
    @Size(max = 100, message = "设备名称不能超过 100 个字符")
    @Column(name = "device_name", length = 100)
    private String deviceName;

    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 500, message = "故障描述不能超过 500 个字符")
    @Column(name = "fault_desc", nullable = false, length = 500)
    private String faultDesc;

    /**
     * 故障类型。存的是**字典项的值**（如 {@code MECH}），不是展示文案。
     *
     * <p>允许为空：这张表在加这个字段之前就有工单了，那些行是 NULL
     * （ddl-auto 只加列不回填）。前端按"未分类"处理。
     *
     * <p>存值而不是文案，是为了让字典的展示文案可以随时改
     * （把「机械故障」改成「机械类故障」），历史工单仍然能正确显示 ——
     * 存文案的话，一改文案历史数据就全变成显示不出来的孤儿值了。
     */
    @Size(max = 50, message = "故障类型不能超过 50 个字符")
    @Column(name = "fault_type", length = 50)
    private String faultType;

    /** 工单状态：待受理 / 维修中 / 已完成 / 已关闭。旧数据可能是「待维修」 */
    @Column(name = "repair_status", nullable = false, length = 20)
    private String repairStatus = STATUS_PENDING;

    /** 报修人 */
    @Size(max = 50, message = "报修人不能超过 50 个字符")
    @Column(name = "reporter", length = 50)
    private String reporter;

    /** 维修人，完工时填写 */
    @Size(max = 50, message = "维修人不能超过 50 个字符")
    @Column(name = "repairer", length = 50)
    private String repairer;

    /** 报修时间 */
    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    /** 完工时间 */
    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    /** 维修费用。用 BigDecimal 而不是 double —— 金额计算不能用浮点数 */
    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    @Column(name = "remark", length = 500)
    private String remark;

    // ============================================================
    // 状态流转记录
    //
    // 每次流转都留一个时间点，而不是只存"当前状态"。
    // 有了这几个时间戳，才能回答运维最关心的两个问题：
    //   "报修后多久有人接单"（acceptTime - reportTime）
    //   "接单后修了多久"  （finishTime - acceptTime）
    // 只存状态的话，这两个指标事后完全算不出来。
    //
    // 全部允许为空：老工单没有这些数据，新工单也是逐步填上的。
    // ============================================================

    /** 指派维修人员的时间 */
    @Column(name = "assign_time")
    private LocalDateTime assignTime;

    /** 受理（开始维修）时间 */
    @Column(name = "accept_time")
    private LocalDateTime acceptTime;

    /** 关闭时间 */
    @Column(name = "close_time")
    private LocalDateTime closeTime;

    /** 关闭原因（作废说明 / 归档说明） */
    @Size(max = 200, message = "关闭原因不能超过 200 个字符")
    @Column(name = "close_reason", length = 200)
    private String closeReason;

    /**
     * 维修结果（具体做了什么、修好没有、更换了哪些件）。
     *
     * <p>和 {@link #remark} 的区别：remark 是报修时可以随手填的备注，
     * 这个是完工时必须交代的处理结论。实际使用中经常要写好几行
     * （拆机检查、更换、测试、结论），所以列类型必须给足 ——
     * 用 {@code columnDefinition = "TEXT"} 而不是 {@code @Lob}，
     * 理由见 entity/package-info.java。
     */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "repair_result", columnDefinition = "TEXT")
    private String repairResult;

    // ============================================================
    // AI 智能分析结果
    //
    // 全部允许为空：这张表在引入 AI 之前就已经有数据了，
    // 加 NOT NULL 列会让已有行校验失败（device_type 那个坑已经踩过一次）。
    // ddl-auto=update 只会新增列，不会回填历史数据，所以旧工单这些字段就是 null，
    // 前端按"没有分析结果"处理即可。
    // ============================================================

    /** AI 分析状态：待分析 / 分析中 / 已完成 / 失败 */
    @Column(name = "ai_status", length = 20)
    private String aiStatus = AI_PENDING;

    /** AI 判断的严重程度：高 / 中 / 低 */
    @Column(name = "ai_severity", length = 10)
    private String aiSeverity;

    /** AI 分析出的可能原因，多条用换行分隔 */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "ai_possible_causes", columnDefinition = "TEXT")
    private String aiPossibleCauses;

    /** AI 建议的维修步骤，多条用换行分隔 */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    /** AI 估算的工时（小时） */
    @Column(name = "ai_estimated_hours", precision = 6, scale = 1)
    private BigDecimal aiEstimatedHours;

    /** 分析用的模型名，便于以后换模型时区分结果来源 */
    @Column(name = "ai_model", length = 50)
    private String aiModel;

    /** 分析完成时间 */
    @Column(name = "ai_analyzed_at")
    private LocalDateTime aiAnalyzedAt;

    /** 分析失败的原因。有值就说明这次分析没成功，但工单本身是正常的 */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "ai_error", columnDefinition = "TEXT")
    private String aiError;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getRepairStatus() {
        return repairStatus;
    }

    public void setRepairStatus(String repairStatus) {
        this.repairStatus = repairStatus;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }

    public LocalDateTime getReportTime() {
        return reportTime;
    }

    public void setReportTime(LocalDateTime reportTime) {
        this.reportTime = reportTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getAssignTime() {
        return assignTime;
    }

    public void setAssignTime(LocalDateTime assignTime) {
        this.assignTime = assignTime;
    }

    public LocalDateTime getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(LocalDateTime acceptTime) {
        this.acceptTime = acceptTime;
    }

    public LocalDateTime getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(LocalDateTime closeTime) {
        this.closeTime = closeTime;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getRepairResult() {
        return repairResult;
    }

    public void setRepairResult(String repairResult) {
        this.repairResult = repairResult;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    // ---------- AI 分析字段的 getter / setter ----------

    public String getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(String aiStatus) {
        this.aiStatus = aiStatus;
    }

    public String getAiSeverity() {
        return aiSeverity;
    }

    public void setAiSeverity(String aiSeverity) {
        this.aiSeverity = aiSeverity;
    }

    public String getAiPossibleCauses() {
        return aiPossibleCauses;
    }

    public void setAiPossibleCauses(String aiPossibleCauses) {
        this.aiPossibleCauses = aiPossibleCauses;
    }

    public String getAiSuggestion() {
        return aiSuggestion;
    }

    public void setAiSuggestion(String aiSuggestion) {
        this.aiSuggestion = aiSuggestion;
    }

    public BigDecimal getAiEstimatedHours() {
        return aiEstimatedHours;
    }

    public void setAiEstimatedHours(BigDecimal aiEstimatedHours) {
        this.aiEstimatedHours = aiEstimatedHours;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public LocalDateTime getAiAnalyzedAt() {
        return aiAnalyzedAt;
    }

    public void setAiAnalyzedAt(LocalDateTime aiAnalyzedAt) {
        this.aiAnalyzedAt = aiAnalyzedAt;
    }

    public String getAiError() {
        return aiError;
    }

    public void setAiError(String aiError) {
        this.aiError = aiError;
    }
}
