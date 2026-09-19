package com.yan.backend.dto;

/**
 * 对话中的一条消息。
 *
 * <p>role 取值：system / user / assistant，与 Ollama 的约定一致。
 */
public record AiChatMessage(String role, String content) {
}
