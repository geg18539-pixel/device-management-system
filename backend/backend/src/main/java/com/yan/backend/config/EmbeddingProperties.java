package com.yan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 嵌入模型配置（对应 application.yml 里的 app.ai.embedding.*）。
 *
 * <p>和对话模型（{@code app.ai.model}）分开配：生成和嵌入通常是**两个不同的模型**，
 * 而且换嵌入模型的代价很大 —— 所有已入库的向量都会失效，
 * 必须重新嵌入一遍。所以它的模型名要独立、显眼，不能混在对话配置里。
 */
@Component
@ConfigurationProperties(prefix = "app.ai.embedding")
public class EmbeddingProperties {

    /**
     * 嵌入模型名。
     *
     * <p>默认 {@code nomic-embed-text}，输出 768 维。
     * ⚠️ 这个模型**不在 Ollama 的默认安装里**，需要用户自己
     * {@code ollama pull nomic-embed-text}（约 270MB）。
     */
    private String model = "nomic-embed-text";

    /**
     * 一次请求嵌入多少个文本块。
     *
     * <p>Ollama 的 {@code /api/embed} 支持 input 传数组，所以可以批量。
     * 逐条请求的话，一个几百块的文档就是几百次 HTTP 往返，慢得离谱。
     *
     * <p>也不宜太大：一批太多会让单次请求变长，一旦失败整批白做。
     */
    private int batchSize = 16;

    /**
     * 单次嵌入请求的读超时（秒）。
     *
     * <p>比对话的读超时短：嵌入是纯前向计算、不逐字生成，
     * 一批十几条通常几百毫秒到几秒。给 120 秒已经是很宽的余量了。
     */
    private int timeoutSeconds = 120;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
