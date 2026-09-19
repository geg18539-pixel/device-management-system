package com.yan.backend.service;

import com.yan.backend.dto.AiChatMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface AiChatService {

    /**
     * 流式对话：边从 Ollama 读、边往 outputStream 写。
     *
     * <p>不返回聚合后的字符串，因为那样就失去流式的意义了。
     * 调用方（Controller）把 outputStream 交给 Spring MVC，
     * 由它负责把写入的内容实时推给浏览器。
     *
     * @param messages     完整对话历史（含 system 提示）
     * @param model        模型名，为空则用默认模型
     * @param outputStream 输出流，每收到一段文本就写一次并 flush
     */
    void streamChat(List<AiChatMessage> messages, String model, OutputStream outputStream) throws IOException;

    /** 列出 Ollama 本地已安装的模型，用来验证连通性和让用户切换 */
    List<String> listModels();

    /** 默认模型名 */
    String getDefaultModel();
}
