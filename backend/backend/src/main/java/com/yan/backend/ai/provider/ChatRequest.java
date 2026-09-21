package com.yan.backend.ai.provider;

import java.util.List;
import java.util.Map;

/**
 * 一次流式对话请求。
 *
 * @param model       模型名。传 null 表示用当前配置的默认模型
 * @param messages    对话历史
 * @param tools       工具定义（JSON Schema）。null 或空表示本轮不提供工具
 * @param temperature 随机度
 * @param maxTokens   最多生成多少 token
 * @param think       要不要让模型**先思考再回答**（Ollama 的 {@code think} 参数）。
 *                    <p><b>null 表示"不指定这个字段"</b>，交给模型自己的默认行为 ——
 *                    "不指定"必须是一个能表达的状态：老版本 Ollama 和不支持思考的模型
 *                    收到这个字段可能直接报错，那种情况下调用方就不该带它。
 */
public record ChatRequest(String model,
                          List<ChatMessage> messages,
                          List<Map<String, Object>> tools,
                          double temperature,
                          int maxTokens,
                          Boolean think) {

    /** 不带工具的普通对话 */
    public static ChatRequest plain(String model, List<ChatMessage> messages,
                                    double temperature, int maxTokens, Boolean think) {
        return new ChatRequest(model, messages, null, temperature, maxTokens, think);
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
