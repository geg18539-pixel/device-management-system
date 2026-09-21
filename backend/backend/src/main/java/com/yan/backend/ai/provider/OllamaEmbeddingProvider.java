package com.yan.backend.ai.provider;

import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用本机 Ollama 做向量化。
 *
 * <p>接口是 {@code POST /api/embed}，请求 {@code {model, input}}，
 * {@code input} 可以是**字符串或字符串数组**（所以能批量）。
 * 响应形如：
 * <pre>
 * { "model": "...", "embeddings": [[0.01, -0.02, ...], [...]], ... }
 * </pre>
 *
 * <p>⚠️ <b>{@code embeddings} 永远是二维数组</b> —— 就算只传一个字符串，
 * 拿到的也是 {@code [[...]]} 而不是 {@code [...]}。按一维解析会得到
 * "每个元素是一个数组"，然后取 {@code asDouble()} 全是 0，
 * 向量变成全零、相似度恒为 0，而且**一路不报错**。
 */
@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

    private final AiSettingsService settings;
    private final AiHttpClients clients;

    public OllamaEmbeddingProvider(AiSettingsService settings, AiHttpClients clients) {
        this.settings = settings;
        this.clients = clients;
    }

    @Override
    public String id() {
        return AiSettingsService.PROVIDER_OLLAMA;
    }

    @Override
    public String label() {
        return "本机 Ollama";
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
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            throw new IllegalStateException(shortReason(ex), ex);
        }

        JsonNode embeddings = response == null ? null : response.path("embeddings");
        if (embeddings == null || !embeddings.isArray()) {
            throw new IllegalStateException(
                    "嵌入接口返回的格式不对：没有 embeddings 数组。"
                            + "请确认 Ollama 版本支持 /api/embed（0.1.26 之前只有 /api/embeddings）");
        }

        List<float[]> vectors = new ArrayList<>(batch.size());
        for (JsonNode row : embeddings) {
            if (!row.isArray() || row.isEmpty()) {
                throw new IllegalStateException("嵌入接口返回了一个空的向量");
            }
            float[] vector = new float[row.size()];
            int i = 0;
            for (JsonNode value : row) {
                vector[i++] = (float) value.asDouble();
            }
            vectors.add(vector);
        }

        // ⚠️ 条数必须一致。不一致的话，第 i 个文本块会被贴上第 j 个向量，
        // 而且**不会有任何报错** —— 表现为"检索结果驴唇不对马嘴"，极难往这个方向想
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
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            List<String> names = new ArrayList<>();
            if (root != null) {
                for (JsonNode item : root.path("models")) {
                    String name = item.path("name").asString();
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }
            return names;
        } catch (Exception ex) {
            throw new IllegalStateException(shortReason(ex), ex);
        }
    }

    /**
     * 把异常翻译成一句用户能照着做的话。
     *
     * <p>连不上和没拉模型都会以异常的形式冒出来，但处理动作完全不同，
     * 所以这里主动去查一次 {@code /api/tags} 来区分。
     */
    private String shortReason(Exception ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();

        List<String> models = listModelsQuietly();
        if (models == null) {
            return "连接不上本地 Ollama（地址：" + settings.embeddingBaseUrl() + "）。"
                    + "请确认它正在运行（命令行执行 ollama list 能列出模型）。"
                    + "原始错误：" + detail;
        }
        String model = modelName();
        boolean found = models.stream().anyMatch(m -> m.equals(model) || m.startsWith(model + ":"));
        if (!found) {
            return "Ollama 里没有嵌入模型「" + model + "」。请先执行：ollama pull " + model
                    + "（当前已有模型：" + String.join("、", models) + "）";
        }
        return "调用嵌入模型失败：" + detail;
    }

    /** 查模型列表用于诊断。查不到就返回 null，表示连 Ollama 本身都够不到 */
    private List<String> listModelsQuietly() {
        try {
            JsonNode root = client().get()
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            List<String> names = new ArrayList<>();
            if (root != null) {
                for (JsonNode item : root.path("models")) {
                    String name = item.path("name").asString();
                    if (name != null && !name.isBlank()) {
                        names.add(name);
                    }
                }
            }
            return names;
        } catch (Exception ex) {
            log.debug("查询 Ollama 模型列表失败（仅用于诊断）", ex);
            return null;
        }
    }

    private RestClient client() {
        return clients.forBaseUrl(settings.embeddingBaseUrl(),
                Duration.ofSeconds(Math.max(10, settings.embeddingTimeoutSeconds())));
    }
}
