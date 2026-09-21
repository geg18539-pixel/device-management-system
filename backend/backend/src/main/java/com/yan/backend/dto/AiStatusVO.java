package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前生效的 AI 配置摘要（**脱敏**）。
 *
 * <p><b>⚠️ 这个类里没有、也永远不会有 API Key 字段。</b>
 * 密钥只从环境变量 / yml 读，不进数据库也不出接口 ——
 * 界面上只需要知道"配没配"（{@code apiKeyConfigured}），
 * 知道内容既没用又多一处泄露面。
 *
 * <p>返回"生效值"而不是"库里的值"：界面上要显示的是**实际在用哪一家、哪个地址**，
 * 而不是某个配置项的字面内容。两者不一样时（库里的 base-url 是空的、
 * 跟着 yml 或环境变量走），管理员更需要看到前者。
 */
public class AiStatusVO {

    /** 可选的提供方，给界面上的下拉用 */
    private List<Option> providers = new ArrayList<>();

    private ProviderStatus chat;
    private ProviderStatus embedding;

    /** 一个下拉选项 */
    public static class Option {
        private String value;
        private String label;

        public Option() {
        }

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    /** 某一类用途（对话 / 嵌入）当前生效的配置 */
    public static class ProviderStatus {
        private String provider;
        private String providerLabel;
        /** **生效的**服务地址（库里没配就用 yml / 环境变量的） */
        private String baseUrl;
        /** **生效的**模型名 */
        private String model;
        /** 密钥配没配。只有布尔值，不回显内容 */
        private boolean apiKeyConfigured;
        /** 支不支持函数调用。只有对话侧有意义 */
        private boolean supportsTools;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getProviderLabel() {
            return providerLabel;
        }

        public void setProviderLabel(String providerLabel) {
            this.providerLabel = providerLabel;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isApiKeyConfigured() {
            return apiKeyConfigured;
        }

        public void setApiKeyConfigured(boolean apiKeyConfigured) {
            this.apiKeyConfigured = apiKeyConfigured;
        }

        public boolean isSupportsTools() {
            return supportsTools;
        }

        public void setSupportsTools(boolean supportsTools) {
            this.supportsTools = supportsTools;
        }
    }

    public List<Option> getProviders() {
        return providers;
    }

    public void setProviders(List<Option> providers) {
        this.providers = providers;
    }

    public ProviderStatus getChat() {
        return chat;
    }

    public void setChat(ProviderStatus chat) {
        this.chat = chat;
    }

    public ProviderStatus getEmbedding() {
        return embedding;
    }

    public void setEmbedding(ProviderStatus embedding) {
        this.embedding = embedding;
    }
}
