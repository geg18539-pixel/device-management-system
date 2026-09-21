package com.yan.backend.ai.provider;

import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按当前配置解析出该用哪个提供方。
 *
 * <p>所有 AI 功能都从这里拿提供方，不直接依赖具体实现 ——
 * 加一家新的只需要写一个 {@code @Component implements LlmProvider}，
 * 它会自动被 Spring 收集进来，这里一行都不用改。
 *
 * <p><b>配置里写了不认识的提供方时不会报错，而是退回第一个可用的并打一条 WARN。</b>
 * 理由：配置里打错一个字（{@code opanai}）就让整个 AI 功能全线报错，
 * 排查成本远高于"用回默认 + 界面上显示当前生效的是哪个"。
 * 这个选错的状况会出现在 {@code /api/ai/status} 的提示里，
 * 不会像静默降级那样无人知晓。
 */
@Component
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final Map<String, LlmProvider> llmProviders;
    private final Map<String, EmbeddingProvider> embeddingProviders;
    private final AiSettingsService settings;

    public AiProviderRegistry(List<LlmProvider> llmProviders,
                              List<EmbeddingProvider> embeddingProviders,
                              AiSettingsService settings) {
        // 用 LinkedHashMap 保持 Spring 注入的顺序，这样"退回第一个"是确定的，
        // 不会因为某次启动顺序不同而换了一家
        this.llmProviders = llmProviders.stream().collect(Collectors.toMap(
                LlmProvider::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.embeddingProviders = embeddingProviders.stream().collect(Collectors.toMap(
                EmbeddingProvider::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.settings = settings;
    }

    // ============================================================
    // 对话
    // ============================================================

    public LlmProvider currentLlm() {
        return resolve(llmProviders, settings.chatProvider(), "对话");
    }

    public LlmProvider llm(String id) {
        return resolve(llmProviders, id, "对话");
    }

    public List<LlmProvider> allLlm() {
        return List.copyOf(llmProviders.values());
    }

    // ============================================================
    // 嵌入
    // ============================================================

    public EmbeddingProvider currentEmbedding() {
        return resolve(embeddingProviders, settings.embeddingProvider(), "嵌入");
    }

    public EmbeddingProvider embedding(String id) {
        return resolve(embeddingProviders, id, "嵌入");
    }

    public List<EmbeddingProvider> allEmbedding() {
        return List.copyOf(embeddingProviders.values());
    }

    // ============================================================

    private <T> T resolve(Map<String, T> candidates, String id, String purpose) {
        if (candidates.isEmpty()) {
            throw new IllegalStateException("没有任何可用的" + purpose + "提供方实现");
        }
        if (id != null && candidates.containsKey(id)) {
            return candidates.get(id);
        }
        T fallback = candidates.values().iterator().next();
        log.warn("配置里的{}提供方「{}」不存在，已退回默认的。可选值：{}",
                purpose, id, String.join("、", candidates.keySet()));
        return fallback;
    }
}
