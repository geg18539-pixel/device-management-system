package com.yan.backend.service.impl;

import com.yan.backend.ai.DeviceDataTools;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.ChatMessage;
import com.yan.backend.ai.provider.ChatRequest;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.ai.provider.LlmRoundResult;
import com.yan.backend.ai.provider.ToolCall;
import com.yan.backend.dto.AiChatMessage;
import com.yan.backend.service.AiChatService;
import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 助手。
 *
 * <p><b>这个类负责"怎么用模型"，提供方负责"怎么跟模型说话"。</b>
 * 工具循环（调几次、什么时候停）是业务逻辑，留在服务层；
 * 流式格式（NDJSON 还是 SSE）、工具调用的字段名、鉴权头这些是协议细节，
 * 全在 {@link LlmProvider} 后面。所以换提供方这个方法一行都不用改。
 *
 * <p>两条和业务绑定的数据来源：
 * <ul>
 *   <li><b>实时数据快照塞进系统提示词</b>（{@code buildSnapshot}）——
 *       不依赖模型的工具调用能力，小模型也能答准，是主力；</li>
 *   <li><b>工具调用</b>——模型主动去查更细的数据。</li>
 * </ul>
 * 两条并用：快照覆盖常见问题，工具兜住需要精确数据的追问。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final DeviceDataTools deviceDataTools;
    private final AiProviderRegistry providerRegistry;
    private final AiSettingsService settings;
    private final ObjectMapper objectMapper;

    public AiChatServiceImpl(DeviceDataTools deviceDataTools,
                             AiProviderRegistry providerRegistry,
                             AiSettingsService settings,
                             ObjectMapper objectMapper) {
        this.deviceDataTools = deviceDataTools;
        this.providerRegistry = providerRegistry;
        this.settings = settings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getDefaultModel() {
        return settings.chatModel();
    }

    @Override
    public void streamChat(List<AiChatMessage> messages, String model, OutputStream outputStream)
            throws IOException {

        LlmProvider provider = providerRegistry.currentLlm();

        List<ChatMessage> chat = new ArrayList<>();
        chat.add(ChatMessage.system(buildSystemPrompt()));
        for (AiChatMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String role = (m.role() == null || m.role().isBlank()) ? "user" : m.role();
            chat.add(new ChatMessage(role, m.content(), null, null, null));
        }

        // 提供方不支持函数调用时**干脆不把工具定义发出去** ——
        // 发了的话有些服务会直接 400，而另一些会返回一堆永远执行不了的工具调用
        List<Map<String, Object>> tools = provider.supportsTools()
                ? deviceDataTools.definitions()
                : List.of();

        int maxRounds = Math.max(1, settings.chatMaxToolRounds());
        long start = System.currentTimeMillis();
        log.info("AI 对话开始：提供方={}, model={}, 用户消息数={}, 启用工具={}",
                provider.id(), model, messages.size(), tools.size());

        try {
            for (int round = 1; round <= maxRounds; round++) {
                ChatRequest request = new ChatRequest(model, chat, tools,
                        settings.chatTemperature(), settings.chatMaxTokens());

                LlmRoundResult result = provider.streamRound(request,
                        chunk -> write(outputStream, chunk));

                if (!result.hasToolCalls()) {
                    // 模型没有要求查数据，说明回答已经写完了
                    log.info("AI 对话结束：轮数={}, 耗时={}ms",
                            round, System.currentTimeMillis() - start);
                    return;
                }

                // 把模型这一轮的"要调工具"消息原样加回历史。
                // 这一步不能省：不带 tool_calls 就追加助手消息，
                // 下一轮模型会看不到自己刚才要求过什么，可能出现重复调用或答非所问。
                // 不同厂商回填时认的字段不同（OpenAI 认 tool_call_id、Ollama 认 tool_name），
                // ChatMessage 两个都带上了，由提供方各取所需。
                chat.add(result.assistantMessage());

                for (ToolCall call : result.toolCalls()) {
                    String toolResult = deviceDataTools.execute(call.name(), parseArguments(call));
                    chat.add(ChatMessage.toolResult(call.id(), call.name(), toolResult));
                }

                log.info("第 {} 轮触发了 {} 个工具调用，继续对话",
                        round, result.toolCalls().size());
            }

            // 走到这里说明工具轮数用完了还没收敛。
            // 与其静默截断，不如明确告诉用户发生了什么。
            outputStream.write(("\n\n[已达到工具调用轮数上限（" + maxRounds
                    + " 轮），先给出目前能确定的信息。可以换个更具体的问法再试。]")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            log.warn("AI 对话达到工具轮数上限");

        } catch (Exception e) {
            log.warn("AI 对话失败：{}", e.getMessage());
            writeErrorText(outputStream, e);
        }
    }

    /**
     * 把工具参数的 JSON 字符串解回 JsonNode。
     *
     * <p>统一约定里 {@code ToolCall} 带的是**字符串**（因为 OpenAI 那边给的就是字符串、
     * 且是分片拼起来的），而 {@code DeviceDataTools.execute} 要的是 JsonNode。
     * 解不出来就给个空对象 —— 工具自己会按"参数缺失"处理并给出提示，
     * 比整个对话失败要好。
     */
    private JsonNode parseArguments(ToolCall call) {
        String json = call.argumentsJson();
        if (!StringUtils.hasText(json)) {
            return objectMapper.readTree("{}");
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.warn("工具 {} 的参数不是合法 JSON，按空参数处理：{}", call.name(), json);
            return objectMapper.readTree("{}");
        }
    }

    /**
     * 系统提示词 = 配置里的角色设定 + 实时数据快照。
     *
     * <p>快照每次请求实时生成，所以模型看到的是当前真实数据，
     * 而不是训练时或上次对话时的旧状态。
     */
    private String buildSystemPrompt() {
        String prompt = settings.chatSystemPrompt();
        String base = StringUtils.hasText(prompt)
                ? prompt
                : "你是设备管理系统内置的助手。回答请简洁、分点、用中文。";
        return base + "\n\n" + deviceDataTools.buildSnapshot();
    }

    /**
     * 把一段文本写进响应流。
     *
     * <p>回调接口不能抛检查异常，所以把 {@link IOException} 包成
     * {@link UncheckedIOException} 出去，由外层统一转成"AI 服务不可用"。
     */
    private void write(OutputStream outputStream, String chunk) {
        try {
            outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
            // flush 不能省：不 flush 内容会留在缓冲区里，
            // 直到整个响应结束才发出去，流式就退化成"等很久然后一次性出现"
            outputStream.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void writeErrorText(OutputStream outputStream, Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String text = "\n\n[AI 服务不可用：" + reason + "]";
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException ignored) {
            // 浏览器那边已经断了，没什么可做的
        }
    }

    @Override
    public List<String> listModels() {
        // 直接把提供方的异常抛出去 —— 它的消息里已经说清了是哪一家、哪个地址、
        // 以及"要不要配 API Key"，这里再包一层只会把有用的话盖掉
        return providerRegistry.currentLlm().listModels();
    }
}
