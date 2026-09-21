package com.yan.backend.ai.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * 一个大模型提供方（本机 Ollama、或任意 OpenAI 兼容服务）。
 *
 * <p>接口刻意只描述"怎么跟这一家说话"，不含任何业务概念 ——
 * 工具循环、提示词、什么时候停，全都留在调用方。
 * 这样加一家新提供方只需要实现这两个方法，不用碰业务代码。
 */
public interface LlmProvider {

    /** 稳定标识，配置里写的值（ollama / openai） */
    String id();

    /** 界面上显示的名字 */
    String label();

    /**
     * 支不支持函数调用。
     *
     * <p>不是所有兼容服务都支持 tools（有些自建推理服务就没实现）。
     * 调用方据此决定"要不要把工具定义发出去"以及"模型不调工具时算正常还是算异常"。
     */
    boolean supportsTools();

    /**
     * 流式对话一轮。
     *
     * <p>文本片段边收边交给 {@code onText}，调用方收到就往下游写 ——
     * **不要攒起来一起给**，那样流式就退化成"等很久然后一次性出现"了。
     *
     * @param onText 每收到一段文本回调一次
     * @return 本轮累积的文本 + 模型要求的工具调用
     */
    LlmRoundResult streamRound(ChatRequest request, Consumer<String> onText);

    /**
     * 结构化输出：要一段固定格式的 JSON。
     *
     * <p>⚠️ <b>返回的是一段<u>文本</u>，不保证一定就是合法 JSON。</b>
     * 各家的支持程度差别极大：原生 Ollama 能用完整 JSON Schema 强约束，
     * OpenAI 支持 {@code json_schema}，DeepSeek 只支持 {@code json_object}，
     * 还有不少自建服务干脆忽略这个字段。
     *
     * <p>所以调用方**必须自己再抠一次**（从文本里找出第一个 JSON 对象），
     * 不能假定这里回来的一定是干净的 JSON。这个约定写在这里，
     * 是为了让"换个提供方就解析失败"这件事在写代码时就被想到。
     */
    String structured(StructuredRequest request);

    /** 可用模型列表。同时充当连通性检查 */
    List<String> listModels();
}
