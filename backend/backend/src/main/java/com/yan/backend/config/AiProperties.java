package com.yan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 相关配置（对应 application.yml 里的 app.ai.*）。
 *
 * <p><b>按用途分成 chat / embedding 两组</b>，而不是像以前那样一堆扁平键。
 * 因为这两件事现在可以**分别接不同的提供方**：对话用云端 API、
 * 嵌入用本机 Ollama 是很常见的搭配（嵌入量大、按量计费不划算，
 * 而对话次数少、要质量）。
 *
 * <p><b>这个类里的是「默认值」，不是「生效值」。</b>
 * provider / base-url / model 这几项会被系统参数（sys_config）覆盖 ——
 * 管理员在「系统设置 → AI 模型」里改完即时生效。
 * 真正取生效值请走 {@code AiSettingsService}，不要直接注入这个类。
 * yml 里的值是**兜底**：库里那一行被删掉时用它，系统不会因此跑不起来。
 *
 * <p><b>密钥只在这个类里</b>（进而只从环境变量 / yml 读），
 * 不进数据库、不出现在任何接口响应里。理由见 {@code AiSettingsService}。
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private Chat chat = new Chat();
    private Embedding embedding = new Embedding();

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    /** 对话模型：AI 助手、故障 AI 分析、故障诊断共用 */
    public static class Chat {

        /** ollama / openai */
        private String provider = "ollama";
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:3b";

        /**
         * API Key。**只从环境变量或 yml 读**。
         *
         * <p>本机 Ollama 不需要，留空即可。
         */
        private String apiKey = "";

        private double temperature = 0.7;
        private int maxTokens = 1024;

        /** 系统提示词：让模型的角色定位和这个项目一致 */
        private String systemPrompt = "";

        /** 故障分析用的温度。分析要的是稳定结论不是创意 */
        private double analysisTemperature = 0.3;
        /** 故障分析单次输出的最大 token 数 */
        private int analysisMaxTokens = 800;

        /** 故障诊断用的温度 */
        private double diagnosisTemperature = 0.3;
        /** 故障诊断单次输出的最大 token 数 */
        private int diagnosisMaxTokens = 900;

        /**
         * 首页摘要用的温度。
         *
         * <p>比分析和诊断略微活一点（0.4 对 0.3）：它输出的是一段给人读的话，
         * 太死板会写成"总数：8；正常：5"这种流水账。但也不能高 ——
         * 数字必须原样搬运，措辞活泼不能以牺牲准确为代价。
         */
        private double digestTemperature = 0.4;
        /**
         * 首页摘要单次输出的最大 token 数。
         *
         * <p>刻意给得比其他几项小：要求就是三五句话。
         * 给到 900 反而是在放任它把看板上的每个数字都复述一遍，
         * 那样这段摘要就没有存在价值了（看板上本来就写着）。
         */
        private int digestMaxTokens = 300;

        /**
         * 最多允许模型"查几次数据库"。
         *
         * <p>小模型可能反复要求调工具，不设上限会陷入死循环、
         * 每次都完整走一遍模型推理，非常慢。
         */
        private int maxToolRounds = 3;

        /** 建立 TCP 连接的超时（秒）。只是握手，很快 */
        private int connectTimeoutSeconds = 10;

        /**
         * 等模型生成的总时间（分钟）。
         *
         * <p>3B 模型在 CPU 上生成 500 字左右可能要几十秒到一两分钟，
         * 给得太短会在生成到一半时被掐断，表现为回复不完整。
         */
        private int readTimeoutMinutes = 5;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
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

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public double getAnalysisTemperature() {
            return analysisTemperature;
        }

        public void setAnalysisTemperature(double analysisTemperature) {
            this.analysisTemperature = analysisTemperature;
        }

        public int getAnalysisMaxTokens() {
            return analysisMaxTokens;
        }

        public void setAnalysisMaxTokens(int analysisMaxTokens) {
            this.analysisMaxTokens = analysisMaxTokens;
        }

        public double getDiagnosisTemperature() {
            return diagnosisTemperature;
        }

        public void setDiagnosisTemperature(double diagnosisTemperature) {
            this.diagnosisTemperature = diagnosisTemperature;
        }

        public int getDiagnosisMaxTokens() {
            return diagnosisMaxTokens;
        }

        public void setDiagnosisMaxTokens(int diagnosisMaxTokens) {
            this.diagnosisMaxTokens = diagnosisMaxTokens;
        }

        public double getDigestTemperature() {
            return digestTemperature;
        }

        public void setDigestTemperature(double digestTemperature) {
            this.digestTemperature = digestTemperature;
        }

        public int getDigestMaxTokens() {
            return digestMaxTokens;
        }

        public void setDigestMaxTokens(int digestMaxTokens) {
            this.digestMaxTokens = digestMaxTokens;
        }

        public int getMaxToolRounds() {
            return maxToolRounds;
        }

        public void setMaxToolRounds(int maxToolRounds) {
            this.maxToolRounds = maxToolRounds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutMinutes() {
            return readTimeoutMinutes;
        }

        public void setReadTimeoutMinutes(int readTimeoutMinutes) {
            this.readTimeoutMinutes = readTimeoutMinutes;
        }
    }

    /** 嵌入模型：知识库的向量化用 */
    public static class Embedding {

        private String provider = "ollama";
        private String baseUrl = "http://localhost:11434";

        /**
         * 嵌入模型名。
         *
         * <p>⚠️ 这个值会被**写进每一条向量记录**，用来判断"两条向量能不能比"。
         * 换模型 = 已入库的向量全部失效，需要对每份文档点一次「重新处理」。
         */
        private String model = "nomic-embed-text";

        /** API Key。留空则回退用 chat 的那个 */
        private String apiKey = "";

        /**
         * 一次请求嵌入多少个文本块。
         *
         * <p>两家都支持 input 传数组，所以能批量 ——
         * 逐条请求的话一个几百块的文档就是几百次 HTTP 往返。
         * 也不宜太大：一批太多会让单次请求变长，一旦失败整批白做。
         */
        private int batchSize = 16;

        /** 单次嵌入请求的读超时（秒）。嵌入是纯前向计算，比对话快得多 */
        private int timeoutSeconds = 120;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
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

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
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
}
