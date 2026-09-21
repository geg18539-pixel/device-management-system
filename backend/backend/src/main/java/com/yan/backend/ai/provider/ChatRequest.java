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
 */
public record ChatRequest(String model,
                          List<ChatMessage> messages,
                          List<Map<String, Object>> tools,
                          double temperature,
                          int maxTokens) {

    /** 不带工具的普通对话 */
    public static ChatRequest plain(String model, List<ChatMessage> messages,
                                    double temperature, int maxTokens) {
        return new ChatRequest(model, messages, null, temperature, maxTokens);
    }

    public boolean hasTools() {
        return tools != null && !tools.isEmpty();
    }
}
