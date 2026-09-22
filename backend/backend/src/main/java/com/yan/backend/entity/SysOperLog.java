package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 操作日志。
 *
 * <p>注意这里**没有记录请求体**。新增用户、修改密码这类接口的 body 里有明文密码，
 * 写进日志表等于把密码泄露到数据库里 —— 而且日志表通常权限更宽松、留存更久。
 * 要记录 body 的话，必须逐字段脱敏之后才行。
 * 当前记录的 URL + 查询串 + 方法签名已经足够定位"谁在什么时候动了什么"。
 */
@Entity
@Table(name = "sys_oper_log")
public class SysOperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作模块，取自 @Log 的 title */
    @Column(name = "title", length = 50)
    private String title;

    /**
     * 「越权访问被拒绝」这条记录的标题。
     *
     * <p>收在实体上而不是两边各写一遍字符串：写入点在
     * {@code JwtInterceptor.recordDenied}，读取点是后台概览的
     * 「今日越权被拒」统计。**两边不一致不会报错**，只会让那个数字恒为 0 ——
     * 而"0 次越权"看起来恰恰是个好消息，不会有人去查。
     */
    public static final String TITLE_DENIED = "越权访问被拒绝";

    /** 操作结果。写入点在 LogAspect 和 JwtInterceptor */
    public static final String STATUS_SUCCESS = "成功";
    public static final String STATUS_FAILED = "失败";

    /** 业务类型：INSERT / UPDATE / DELETE / OTHER */
    @Column(name = "business_type", length = 20)
    private String businessType;

    /** 请求方式：GET / POST / PUT / DELETE */
    @Column(name = "request_method", length = 10)
    private String requestMethod;

    /** 请求 URL（含查询串） */
    @Column(name = "request_url", length = 500)
    private String requestUrl;

    /** 被调用的方法签名，如 SysUserController.create(..) */
    @Column(name = "method", length = 200)
    private String method;

    /** 操作人 id。定时任务等无登录态的场景会是 null */
    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", length = 50)
    private String operatorName;

    @Column(name = "ip", length = 50)
    private String ip;

    /** 执行结果：成功 / 失败 */
    @Column(name = "status", length = 10)
    private String status;

    /** 失败时的异常信息，成功时为 null */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    /** 耗时（毫秒） */
    @Column(name = "cost_time")
    private Long costTime;

    @CreationTimestamp
    @Column(name = "oper_time", nullable = false, updatable = false)
    private LocalDateTime operTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Long getCostTime() {
        return costTime;
    }

    public void setCostTime(Long costTime) {
        this.costTime = costTime;
    }

    public LocalDateTime getOperTime() {
        return operTime;
    }

    public void setOperTime(LocalDateTime operTime) {
        this.operTime = operTime;
    }
}
