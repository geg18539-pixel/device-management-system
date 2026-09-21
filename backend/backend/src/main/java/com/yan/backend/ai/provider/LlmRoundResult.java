package com.yan.backend.ai.provider;

import java.util.List;

/**
 * 流式对话**一轮**的结果。
 *
 * <p>"一轮"指一次请求-响应往返。模型可能在这一轮里同时说了话又要求调工具，
 * 所以文本和工具调用都要带回来；工具循环本身（调几次、什么时候停）
 * 留在调用方那边 —— 那是业务逻辑，不该塞进提供方。
 *
 * @param text             本轮累积的完整文本
 * @param toolCalls        本轮模型要求的工具调用。空表示回答已经写完
 * @param assistantMessage 本轮的助手消息，**必须原样回填进历史**再发下一轮
 */
public record LlmRoundResult(String text, List<ToolCall> toolCalls, ChatMessage assistantMessage) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
