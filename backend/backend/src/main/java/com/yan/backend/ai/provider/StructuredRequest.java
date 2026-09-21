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
 * @param think      要不要让模型先思考（Ollama 的 {@code think} 参数）。
 *                   <p>结构化输出这条路径上**通常应该关掉**：任务本身是"按给定结构
 *                   把已有信息填进去"，不是需要推理的开放问题，而思考会先把
 *                   token 预算和时间花在一大段中间过程上。详见 {@code ChatRequest#think}
 *                   关于 {@code null} 语义的说明。
 */
public record StructuredRequest(String model,
                                List<ChatMessage> messages,
                                Map<String, Object> jsonSchema,
                                double temperature,
                                int maxTokens,
                                Boolean think) {
}
