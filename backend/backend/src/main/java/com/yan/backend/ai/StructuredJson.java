package com.yan.backend.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 从模型返回的**一段文本**里抠出一个 JSON 对象。
 *
 * <h3>为什么必须有这么一层</h3>
 *
 * <p>{@code LlmProvider#structured} 的约定是「返回的是一段文本，<b>不保证</b>是合法 JSON」——
 * 各家的结构化输出支持程度差别极大：原生 Ollama 能用 JSON Schema 强约束，
 * OpenAI 支持 {@code json_object}，还有不少自建服务干脆忽略这个参数。
 * 所以调用方必须自己再抠一次，不能假定拿到的就是干净 JSON。
 *
 * <p>三层兜底：直接解析 → 去掉 markdown 代码围栏 → 取第一个 {@code &#123;} 到
 * 最后一个 {@code &#125;}。多写这十几行，换来的是**换一个提供方不用改代码**。
 *
 * <p>抽成独立的 bean 而不是各自写一份：故障分析和自然语言问数都要用，
 * 复制两份的话，以后补第四层兜底必然只改一处。
 */
@Component
public class StructuredJson {

    private final ObjectMapper objectMapper;

    public StructuredJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws IllegalStateException 三层兜底都失败时。<b>调用方要把它当成
     *         "模型没按格式回答"来处理</b>，而不是当成服务器故障 ——
     *         小模型偶尔答非所问是常态，不该让用户看到 500。
     */
    public JsonNode extract(String content) {
        String text = content == null ? "" : content.strip();

        JsonNode direct = tryParse(text);
        if (direct != null) {
            return direct;
        }

        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                JsonNode unfenced = tryParse(text.substring(firstNewline + 1, lastFence).strip());
                if (unfenced != null) {
                    return unfenced;
                }
            }
        }

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode embedded = tryParse(text.substring(start, end + 1));
            if (embedded != null) {
                return embedded;
            }
        }

        throw new IllegalStateException("模型返回的不是合法 JSON：" + truncate(content, 200));
    }

    private JsonNode tryParse(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            // readTree 对 "" 之类的输入会返回 null 而不是抛异常，这里一并挡掉
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 拼错误消息用，别把整段模型输出塞进日志 */
    public static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
