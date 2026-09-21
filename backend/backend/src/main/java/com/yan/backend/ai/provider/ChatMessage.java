package com.yan.backend.ai.provider;

import java.util.List;

/**
 * 一条对话消息。
 *
 * <p>这是**跨提供方的中性表示** —— 不同厂商的消息格式差别不小
 * （工具结果的字段名、工具调用的参数是对象还是字符串），
 * 统一收在这里，由各个提供方在发请求时翻译成自己那套。
 *
 * @param role       system / user / assistant / tool
 * @param content    正文。工具调用轮里 assistant 的 content 可能是空的
 * @param toolCalls  assistant 要求调用的工具
 * @param toolCallId tool 消息对应哪一次调用。**OpenAI 系要求必须回填这个 id**
 * @param toolName   tool 消息对应的工具名。**Ollama 要的是这个而不是 id**
 */
public record ChatMessage(String role,
                          String content,
                          List<ToolCall> toolCalls,
                          String toolCallId,
                          String toolName) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }

    /**
     * 模型要求调用工具的那条 assistant 消息。
     *
     * <p>**必须原样回填进历史**：不带 tool_calls 就追加一条助手消息，
     * 下一轮模型会看不到自己刚才要求过什么，表现为重复调用或者答非所问。
     */
    public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null, null);
    }

    /**
     * 工具执行结果。
     *
     * <p>两个标识都给上：Ollama 认 {@code tool_name}，OpenAI 系认 {@code tool_call_id}，
     * 由提供方各取所需 —— 在这里分支的话，调用方就得知道自己用的是哪家。
     */
    public static ChatMessage toolResult(String toolCallId, String toolName, String content) {
        return new ChatMessage("tool", content, null, toolCallId, toolName);
    }
}
