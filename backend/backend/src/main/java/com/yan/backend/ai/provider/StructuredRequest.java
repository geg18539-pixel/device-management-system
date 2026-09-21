package com.yan.backend.ai.provider;

import java.util.List;
import java.util.Map;

/**
 * 一次**结构化输出**请求（要模型返回固定的 JSON，而不是随便说话）。
 *
 * <p>单独一个类型而不是复用 {@link ChatRequest}：两者的诉求正相反 ——
 * 对话要的是自由文本 + 工具调用，结构化输出要的是"别多说话，就给我一段 JSON"。
 * 混在一起的话，每个调用点都得传一堆用不上的字段。
 *
 * @param jsonSchema 期望的 JSON Schema。见 {@code LlmProvider#structured} 的说明：
 *                   **不是所有提供方都支持约束**，所以调用方仍然必须能容忍自由文本
 */
public record StructuredRequest(String model,
                                List<ChatMessage> messages,
                                Map<String, Object> jsonSchema,
                                double temperature,
                                int maxTokens) {
}
