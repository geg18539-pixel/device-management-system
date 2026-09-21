package com.yan.backend.ai.provider;

import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容服务的向量化接口。
 *
 * <pre>
 * POST {baseUrl}/embeddings
 * 请求:  {"model": "text-embedding-3-small", "input": ["文本1", "文本2"]}
 * 响应:  {"data": [{"index": 0, "embedding": [...]}, {"index": 1, "embedding": [...]}], ...}
 * </pre>
 *
 * <p>⚠️ <b>必须按 {@code index} 排序，不能按数组顺序取。</b>
 * OpenAI 的接口文档明确说了返回顺序不保证（批量请求会被并行处理）。
 * 不排的话，第 i 个文本块会被贴上第 j 个向量 ——
 * 而且**不会有任何报错**，表现为"检索结果驴唇不对马嘴"，极难往这个方向想。
 * 排序是提供方的责任：调用方只认"返回的列表和入参一一对应"这个约定。
 */
@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingProvider.class);

    /** 路径不含版本段，理由见 OpenAiLlmProvider 的说明 */
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String MODELS_PATH = "/models";

    private final AiSettingsService settings;
    private final AiHttpClients clients;

    public OpenAiEmbeddingProvider(AiSettingsService settings, AiHttpClients clients) {
        this.settings = settings;
        this.clients = clients;
    }

    @Override
    public String id() {
        return AiSettingsService.PROVIDER_OPENAI;
    }

    @Override
    public String label() {
        return "OpenAI 兼容服务";
    }

    @Override
    public String modelName() {
        return settings.embeddingModel();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int batchSize = Math.max(1, settings.embeddingBatchSize());
        List<float[]> all = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(texts.size(), start + batchSize);
            all.addAll(embedBatch(texts.subList(start, end)));
        }
        return all;
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName());
        payload.put("input", batch);

        JsonNode response;
        try {
            response = client().post()
                    .uri(EMBEDDINGS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuth)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw wrap(ex);
        }

        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) {
            throw new IllegalStateException(
                    "嵌入接口返回的格式不对：没有 data 数组（提供方：OpenAI 兼容）");
        }

        // ★ 按 index 排序再取。顺序不保证是接口的明文约定
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : data) {
            items.add(item);
        }
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(0)));

        List<float[]> vectors = new ArrayList<>(batch.size());
        for (JsonNode item : items) {
            JsonNode embedding = item.path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalStateException("嵌入接口返回了一个空的向量");
            }
            float[] vector = new float[embedding.size()];
            int i = 0;
            for (JsonNode value : embedding) {
                vector[i++] = (float) value.asDouble();
            }
            vectors.add(vector);
        }

        if (vectors.size() != batch.size()) {
            throw new IllegalStateException(
                    "嵌入结果条数对不上：请求 " + batch.size() + " 条，返回 " + vectors.size() + " 条");
        }
        return vectors;
    }

    @Override
    public List<String> listModels() {
        try {
            JsonNode root = client().get()
                    .uri(MODELS_PATH)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuth)
                    .retrieve()
                    .body(JsonNode.class);
            List<String> models = new ArrayList<>();
            if (root != null) {
                for (JsonNode item : root.path("data")) {
                    String id = item.path("id").asString();
                    if (StringUtils.hasText(id)) {
                        models.add(id);
                    }
                }
            }
            return models;
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    /**
     * 加鉴权头。密钥为空就不加 —— 本机自建服务通常不需要，
     * 硬塞一个 {@code Bearer } 反而可能被拒。
     *
     * <p>用的是 {@code embeddingApiKey()}：它没单独配时会**回退到对话的那个 Key**，
     * 因为大多数场合两边用的是同一家。
     */
    private void applyAuth(org.springframework.http.HttpHeaders headers) {
        String apiKey = settings.embeddingApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.set("Authorization", "Bearer " + apiKey.trim());
        }
    }

    private RestClient client() {
        return clients.forBaseUrl(settings.embeddingBaseUrl(),
                Duration.ofSeconds(Math.max(10, settings.embeddingTimeoutSeconds())));
    }

    private IllegalStateException wrap(Exception ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String hint = StringUtils.hasText(settings.embeddingApiKey())
                ? ""
                : "（当前没有配置 API Key，如果是需要鉴权的服务，请设置环境变量 AI_EMBED_API_KEY，"
                        + "或复用 AI_CHAT_API_KEY）";
        return new IllegalStateException(
                "调用嵌入模型失败（提供方：OpenAI 兼容，模型：" + modelName()
                        + "，地址：" + settings.embeddingBaseUrl()
                        + "，实际请求 " + settings.embeddingBaseUrl() + EMBEDDINGS_PATH + "）"
                        + hint + "。地址错了的话请核对：base-url 要**填到版本段为止**，"
                        + "例如 https://api.deepseek.com/v1、"
                        + "https://dashscope.aliyuncs.com/compatible-mode/v1。"
                        + "原始错误：" + detail, ex);
    }
}
