package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 登录日志。
 *
 * <p><b>刻意不关联 sys_user 表</b>，只存用户名字符串。原因：
 * <ul>
 *   <li>登录失败时可能根本没有这个用户（有人在猜账号），没有 id 可关联；</li>
 *   <li>用户被删除后，历史登录记录还应该留着 —— 那是安全审计资料，
 *       不能因为删了账号就跟着消失。</li>
 * </ul>
 *
 * <p>成功和失败都记。**失败记录往往比成功记录更有价值**：
 * 短时间内同一用户名大量失败就是撞库特征，同一 IP 换不同用户名尝试是扫描特征。
 */
@Entity
@Table(name = "sys_login_log", indexes = {
        @Index(name = "idx_login_log_time", columnList = "login_time"),
        @Index(name = "idx_login_log_username", columnList = "username")
})
public class SysLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 尝试登录的用户名。失败时也记录，哪怕这个用户根本不存在 */
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /** 登录成功时填上用户 id；失败或用户不存在时为 null */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip", length = 50)
    private String ip;

    /**
     * 客户端原始 User-Agent，截断后保存。
     *
     * <p>原样存下来是为了以后需要更精确的分析时有据可查；
     * 页面上展示的是 {@link #device} 那个简化描述。
     */
    @Size(max = 500, message = "User-Agent 不能超过 500 个字符")
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 从 User-Agent 里粗略提取的设备描述，如「Windows / Chrome」 */
    @Column(name = "device", length = 100)
    private String device;

    /** 是否登录成功 */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /** 失败原因，成功时为 null */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @Column(name = "login_time", nullable = false)
    private LocalDateTime loginTime;

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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
