package com.yan.backend.service;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.config.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * AI 配置的**唯一读取入口**。
 *
 * <p><b>取值规则：库里有就用库里的，没有（或那一行为空）就用 yml 的。</b>
 * 和项目里其它系统参数是同一套语义，好处是空库、误删某一项时系统照常跑。
 * 但 base-url 这两个键的默认值刻意留**空串**，因为写死成
 * {@code http://localhost:11434} 会把 Docker 里注入的
 * {@code AI_CHAT_BASE_URL} 盖掉，而管理员在界面上完全看不出为什么改环境变量不生效。
 *
 * <h3>⚠️ 密钥为什么不进数据库</h3>
 *
 * <p>API Key 只从这个类读，而这个类的密钥来源是 {@link AiProperties}，
 * 也就是**只有环境变量和 application.yml** 两个来源。这样做的理由：
 * <ul>
 *   <li>进了库就要面对一串问题：加密存、返回时掩码、日志里不能出现、
 *       备份文件里会带上、导出审计报表时可能被顺手带出去 ——
 *       每一环漏一次密钥就泄露了；</li>
 *   <li>密钥和 JWT 密钥、数据库密码是一类东西：**改错了系统起不来 / 泄露了全完蛋**，
 *       项目里对这类值的既定口径就是"留在 yml 或环境变量，界面上不给改"；</li>
 *   <li>代价只是换密钥要重启，而换密钥本来就是低频且需要慎重的操作。</li>
 * </ul>
 *
 * <p>所以界面上能看到、能改的只有 provider / base-url / model 这些非敏感项；
 * 界面上只会告诉你"密钥配没配"（{@code apiKeyConfigured}），永远不回显内容。
 */
@Service
public class AiSettingsService {

    /** 提供方标识 */
    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_OPENAI = "openai";

    private final SysConfigService configService;
    private final AiProperties properties;

    public AiSettingsService(SysConfigService configService, AiProperties properties) {
        this.configService = configService;
        this.properties = properties;
    }

    // ============================================================
    // 对话
    // ============================================================

    /**
     * 对话提供方。
     *
     * <p>⚠️ 必须用 firstNonBlank 而不是 {@code getString(key, 默认值)} ——
     * 后者只在**键不存在**时回退，键存在但值是空串时会返回空串，
     * 于是提供方解析不出来、静默退回第一个。空串和"没配"在这里是同一个意思。
     */
    public String chatProvider() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_CHAT_PROVIDER, ""),
                properties.getChat().getProvider());
    }

    public String chatBaseUrl() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_CHAT_BASE_URL, ""),
                properties.getChat().getBaseUrl());
    }

    public String chatModel() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_CHAT_MODEL, ""),
                properties.getChat().getModel());
    }

    public double chatTemperature() {
        String raw = configService.getString(
                ConfigKeys.AI_CHAT_TEMPERATURE,
                String.valueOf(properties.getChat().getTemperature()));
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            // 界面上把温度填成 "abc" 时退回默认值，而不是让对话整体不可用
            return properties.getChat().getTemperature();
        }
    }

    /** 对话密钥。**只读，没有任何接口能把它返回出去** */
    public String chatApiKey() {
        return properties.getChat().getApiKey();
    }

    public int chatMaxTokens() {
        return properties.getChat().getMaxTokens();
    }

    public String chatSystemPrompt() {
        return properties.getChat().getSystemPrompt();
    }

    public int chatMaxToolRounds() {
        return properties.getChat().getMaxToolRounds();
    }

    public int chatConnectTimeoutSeconds() {
        return properties.getChat().getConnectTimeoutSeconds();
    }

    public int chatReadTimeoutMinutes() {
        return properties.getChat().getReadTimeoutMinutes();
    }

    public double analysisTemperature() {
        return properties.getChat().getAnalysisTemperature();
    }

    public int analysisMaxTokens() {
        return properties.getChat().getAnalysisMaxTokens();
    }

    public double diagnosisTemperature() {
        return properties.getChat().getDiagnosisTemperature();
    }

    public int diagnosisMaxTokens() {
        return properties.getChat().getDiagnosisMaxTokens();
    }

    public double digestTemperature() {
        return properties.getChat().getDigestTemperature();
    }

    public int digestMaxTokens() {
        return properties.getChat().getDigestMaxTokens();
    }

    /**
     * 要不要让模型先思考再回答。
     *
     * <p>⚠️ 这里**刻意不直接用 {@code getBoolean}**：那一套的语义是
     * "解析不了就回退默认值**并打一条警告**"，而我们的种子值故意是空串
     * （和 provider / base-url / model 一样，表示"不覆盖，跟随配置文件/环境变量"）——
     * 空串在 {@code getBoolean} 眼里就是"解析失败"，于是每次读都会刷一条警告，
     * 而"没配"在这里是完全正常的状态。
     *
     * <p>所以自己读原始字符串判空，规则和其它 AI 配置项保持一致：
     * **库里为空 → 用配置文件/环境变量的值；填了 → 覆盖。**
     */
    public boolean chatThinking() {
        String raw = configService.getString(ConfigKeys.AI_CHAT_THINKING, "");
        if (raw != null && !raw.isBlank()) {
            return Boolean.parseBoolean(raw.trim());
        }
        return properties.getChat().isThinking();
    }

    // ============================================================
    // 嵌入
    // ============================================================

    public String embeddingProvider() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_EMBEDDING_PROVIDER, ""),
                properties.getEmbedding().getProvider());
    }

    public String embeddingBaseUrl() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_EMBEDDING_BASE_URL, ""),
                properties.getEmbedding().getBaseUrl());
    }

    public String embeddingModel() {
        return firstNonBlank(
                configService.getString(ConfigKeys.AI_EMBEDDING_MODEL, ""),
                properties.getEmbedding().getModel());
    }

    /**
     * 嵌入密钥。没单独配就**回退用对话的那个**。
     *
     * <p>大多数场合对话和嵌入用的是同一家的同一个 Key，
     * 逼用户填两遍纯属找麻烦；而写成 {@code embedding.api-key} 是一个显式的
     * "我想分开"的开关 —— 比如对话用 DeepSeek、嵌入用硅基流动，两边 Key 不同。
     */
    public String embeddingApiKey() {
        String own = properties.getEmbedding().getApiKey();
        return StringUtils.hasText(own) ? own : chatApiKey();
    }

    public int embeddingBatchSize() {
        return properties.getEmbedding().getBatchSize();
    }

    public int embeddingTimeoutSeconds() {
        return properties.getEmbedding().getTimeoutSeconds();
    }

    // ============================================================

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }
}
