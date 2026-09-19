package com.yan.backend.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 前端发来的对话请求。
 *
 * <p>把**完整的消息列表**传上来，而不是只传最新一句 —— 因为对话历史保存在前端内存里，
 * 后端做成无状态的：每次请求都当作一次全新的对话处理，不维护会话。
 * 这样不用建表，也不用担心多实例部署时的会话同步。
 *
 * <p>model 留空则用 application.yml 里配的默认模型。
 */
public record AiChatRequest(

        @NotEmpty(message = "消息列表不能为空")
        List<AiChatMessage> messages,

        /** 可选，指定用哪个模型。为空则用 app.ai.model */
        String model
) {
}
