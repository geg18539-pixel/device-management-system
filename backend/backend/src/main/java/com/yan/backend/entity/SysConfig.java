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

import java.time.LocalDateTime;

/**
 * 系统参数（键值对）。
 *
 * <p><b>和 application.yml 的关系：库里的值是"覆盖"，不是"唯一来源"。</b>
 * 读取时先查这张表，查不到才回退到 yml 里的默认值（见 {@code SysConfigService}）。
 * 这么做有两个好处：
 * <ul>
 *   <li>空库、或者管理员误删了某一项，系统不会因此跑不起来 —— 有默认值兜底</li>
 *   <li>新加一个参数时只改 yml 就够了，不必同步改数据库</li>
 * </ul>
 *
 * <p><b>哪些参数不该放进来：</b>JWT 密钥、数据库连接、Ollama 地址这类**基建和安全**
 * 相关的配置留在 yml。它们改错的后果是"整个系统起不来"或"认证被绕过"，
 * 不适合让界面上的一个输入框就能改掉。
 */
@Entity
@Table(name = "sys_config")
public class SysConfig {

    // ---------- 值类型常量 ----------
    /** 普通字符串 */
    public static final String TYPE_STRING = "STRING";
    /** 整数。前端渲染成数字输入框，后端保存前会校验能不能解析成整数 */
    public static final String TYPE_NUMBER = "NUMBER";
    /** 布尔。值只接受 true / false */
    public static final String TYPE_BOOLEAN = "BOOLEAN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 参数键，全局唯一。
     *
     * <p>用点号分层的命名（如 {@code security.password.valid-days}），
     * 刻意和 application.yml 里的路径保持一致 —— 这样看代码的人
     * 一眼能对上"这个参数对应 yml 里哪一项"。
     */
    @NotBlank(message = "参数键不能为空")
    @Size(max = 100, message = "参数键不能超过 100 个字符")
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /**
     * 参数值。**统一按字符串存**。
     *
     * <p>不按类型拆成多个列（valueInt / valueBool…）：那样每加一种类型就要改表结构。
     * 需要数字时由服务层的 {@code getInt} 负责转换，转不了就回退默认值。
     */
    @Size(max = 500, message = "参数值不能超过 500 个字符")
    @Column(name = "config_value", length = 500)
    private String configValue;

    /** 展示名，如「密码有效期（天）」 */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 100, message = "参数名称不能超过 100 个字符")
    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    /** 分组，页面上按它分块展示，如「系统信息」「安全策略」「提醒阈值」 */
    @Size(max = 50, message = "分组名不能超过 50 个字符")
    @Column(name = "config_group", length = 50)
    private String configGroup;

    /** 值类型：STRING / NUMBER / BOOLEAN。决定前端用什么输入控件、后端怎么校验 */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = TYPE_STRING;

    /**
     * 是否内置参数。
     *
     * <p>内置项**不允许删除**，只能改值 —— 它们对应代码里 {@code getInt(key, 默认值)}
     * 的调用点，删掉之后代码不会报错，只是悄悄用回默认值，
     * 管理员会以为"我明明配了却不生效"。
     */
    @Column(name = "built_in", nullable = false)
    private Boolean builtIn = Boolean.FALSE;

    /** 说明：这个参数是干什么的、改大了会怎样。页面上直接展示给管理员 */
    @Size(max = 300, message = "说明不能超过 300 个字符")
    @Column(name = "remark", length = 300)
    private String remark;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

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

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Boolean getBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(Boolean builtIn) {
        this.builtIn = builtIn;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
}
