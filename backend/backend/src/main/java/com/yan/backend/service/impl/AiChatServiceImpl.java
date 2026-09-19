package com.yan.backend.service.impl;

import com.yan.backend.ai.DeviceDataTools;
import com.yan.backend.dto.AiChatMessage;
import com.yan.backend.service.AiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 Ollama 的流式响应转发给浏览器，并在中间插入"查数据"的环节。
 *
 * <p>整体是一个 **Agent 循环**：
 * <pre>
 *   请求（带 tools）
 *     ├─ 模型直接回答          → 边收边转发，结束
 *     └─ 模型要求调用工具      → 执行工具查数据库
 *                               → 把 assistant 的 tool_calls 消息和工具结果回填
 *                               → 再次请求（最多 maxToolRounds 轮）
 * </pre>
 *
 * <p>关于 JSON 解析器：用的是 **Jackson 3**（{@code tools.jackson.databind.*}）。
 * Boot 4 的 Spring MVC 默认用 Jackson 3，而 classpath 上同时存在 Jackson 2
 * （jjwt 以 runtime 作用域带进来的），注入错的那个会在运行时才暴露问题。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final DeviceDataTools deviceDataTools;
    private final String defaultModel;
    private final String systemPrompt;
    private final double temperature;
    private final int maxTokens;
    private final int maxToolRounds;

    public AiChatServiceImpl(RestClient ollamaRestClient,
                             ObjectMapper objectMapper,
                             DeviceDataTools deviceDataTools,
                             @Value("${app.ai.model}") String defaultModel,
                             @Value("${app.ai.system-prompt:}") String systemPrompt,
                             @Value("${app.ai.temperature:0.7}") double temperature,
                             @Value("${app.ai.max-tokens:1024}") int maxTokens,
                             @Value("${app.ai.max-tool-rounds:3}") int maxToolRounds) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.deviceDataTools = deviceDataTools;
        this.defaultModel = defaultModel;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.maxToolRounds = maxToolRounds;
    }

    @Override
    public String getDefaultModel() {
        return defaultModel;
    }

    @Override
    public void streamChat(List<AiChatMessage> messages, String model, OutputStream outputStream)
            throws IOException {

        String actualModel = (model == null || model.isBlank()) ? defaultModel : model.trim();

        List<Object> chatMessages = new ArrayList<>();
        chatMessages.add(message("system", buildSystemPrompt()));
        for (AiChatMessage m : messages) {
            if (m == null || m.content() == null || m.content().isBlank()) {
                continue;
            }
            String role = (m.role() == null || m.role().isBlank()) ? "user" : m.role();
            chatMessages.add(message(role, m.content()));
        }

        List<Map<String, Object>> tools = deviceDataTools.definitions();

        long start = System.currentTimeMillis();
        log.info("AI 对话开始：model={}, 用户消息数={}, 启用工具={}",
                actualModel, messages.size(), tools.size());

        try {
            for (int round = 1; round <= maxToolRounds; round++) {
                RoundResult result = doOneRound(actualModel, chatMessages, tools, outputStream);

                if (result.toolCalls.isEmpty()) {
                    // 模型没有要求查数据，说明回答已经写完了
                    log.info("AI 对话结束：model={}, 轮数={}, 耗时={}ms",
                            actualModel, round, System.currentTimeMillis() - start);
                    return;
                }

                // 把模型这一轮的"要调工具"消息原样加回历史。
                // 这一步不能省：如果不带 tool_calls 就追加助手消息，
                // 下一轮模型会看不到自己刚才要求过什么，可能出现重复调用或答非所问。
                chatMessages.add(result.assistantMessage());

                for (ToolCall call : result.toolCalls) {
                    String toolResult = deviceDataTools.execute(call.name(), call.arguments());
                    Map<String, Object> toolMessage = new LinkedHashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_name", call.name());
                    toolMessage.put("content", toolResult);
                    chatMessages.add(toolMessage);
                }

                log.info("第 {} 轮触发了 {} 个工具调用，继续对话", round, result.toolCalls.size());
            }

            // 走到这里说明工具轮数用完了还没收敛。
            // 与其静默截断，不如明确告诉用户发生了什么。
            outputStream.write(("\n\n[已达到工具调用轮数上限（" + maxToolRounds
                    + " 轮），先给出目前能确定的信息。可以换个更具体的问法再试。]")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            log.warn("AI 对话达到工具轮数上限，model={}", actualModel);

        } catch (Exception e) {
            log.warn("AI 对话失败：{}", e.getMessage());
            writeErrorText(outputStream, e);
        }
    }

    // ============================================================
    // 单轮请求与转发
    // ============================================================

    /** 一轮请求的结果：本轮输出的文本 + 模型要求调用的工具 */
    private record RoundResult(String content,
                               List<ToolCall> toolCalls,
                               Map<String, Object> assistantMessage) {
    }

    /** 一次工具调用请求 */
    private record ToolCall(String name, JsonNode arguments,
                            Map<String, Object> rawCall) {
    }

    private RoundResult doOneRound(String model, List<Object> chatMessages,
                                   List<Map<String, Object>> tools,
                                   OutputStream outputStream) {

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", chatMessages);
        payload.put("tools", tools);
        // Ollama 的 stream 默认就是 true，显式写出来更清楚。
        // 传 false 会拿到一个完整 JSON 对象而不是 NDJSON 流，下面的逐行解析会一行都读不到。
        payload.put("stream", true);
        payload.put("options", Map.of(
                "temperature", temperature,
                "num_predict", maxTokens));

        return ollamaRestClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .body(payload)
                .exchange((request, response) -> relay(response.getBody(), outputStream));
    }

    /**
     * 逐行读取 Ollama 的 NDJSON 流：文本分片边收边转发给浏览器，
     * 同时把 tool_calls 累积下来交给外层执行。
     */
    private RoundResult relay(InputStream body, OutputStream outputStream) {
        StringBuilder content = new StringBuilder();
        // 按 index 归并：一次工具调用的 name 和 arguments 可能分散在不同分片里
        Map<Integer, Map<String, Object>> callsByIndex = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                JsonNode node;
                try {
                    node = objectMapper.readTree(line);
                } catch (Exception parseError) {
                    // 单行解析失败不中断整轮对话
                    log.debug("跳过无法解析的 NDJSON 行: {}", line);
                    continue;
                }

                JsonNode messageNode = node.path("message");

                String chunk = messageNode.path("content").asString();
                if (chunk != null && !chunk.isEmpty()) {
                    content.append(chunk);
                    try {
                        outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                        // flush 不能省：不 flush 内容会攒在缓冲区里，
                        // 直到整个响应结束才发出去，流式就退化成"等很久然后一次性出现"
                        outputStream.flush();
                    } catch (IOException e) {
                        throw new IllegalStateException("写响应失败: " + e.getMessage(), e);
                    }
                }

                JsonNode toolCalls = messageNode.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        JsonNode fn = tc.path("function");
                        int index = fn.path("index").asInt(callsByIndex.size());
                        Map<String, Object> call = callsByIndex.computeIfAbsent(
                                index, k -> new LinkedHashMap<>());
                        call.put("type", "function");

                        Map<String, Object> function = new LinkedHashMap<>();
                        function.put("index", index);
                        if (!fn.path("name").isMissingNode()) {
                            function.put("name", fn.path("name").asString());
                        }
                        if (!fn.path("arguments").isMissingNode()) {
                            function.put("arguments", fn.path("arguments"));
                        }
                        call.put("function", function);
                    }
                }

                if (node.path("done").asBoolean(false)) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 Ollama 响应流失败: " + e.getMessage(), e);
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        List<Map<String, Object>> rawCalls = new ArrayList<>();
        for (Map<String, Object> call : callsByIndex.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) call.get("function");
            String name = (String) function.get("name");
            JsonNode args = (JsonNode) function.get("arguments");
            rawCalls.add(call);
            toolCalls.add(new ToolCall(name, args, call));
        }

        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", content.toString());
        if (!rawCalls.isEmpty()) {
            assistantMessage.put("tool_calls", rawCalls);
        }

        return new RoundResult(content.toString(), toolCalls, assistantMessage);
    }

    // ============================================================
    // 辅助
    // ============================================================

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /**
     * 系统提示词 = 配置里的角色设定 + 实时数据快照。
     *
     * <p>快照每次请求实时生成，所以模型看到的是当前真实数据，
     * 而不是训练时或上次对话时的旧状态。
     */
    private String buildSystemPrompt() {
        String base = (systemPrompt == null || systemPrompt.isBlank())
                ? "你是设备管理系统内置的助手。回答请简洁、分点、用中文。"
                : systemPrompt;
        return base + "\n\n" + deviceDataTools.buildSnapshot();
    }

    private void writeErrorText(OutputStream outputStream, Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String text = "\n\n[无法连接本地 Ollama：" + reason
                + "。请确认 Ollama 已启动（命令 ollama list 能列出模型），"
                + "以及 app.ai.base-url 配置正确。]";
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException ignored) {
            // 浏览器那边已经断了，没什么可做的
        }
    }

    @Override
    public List<String> listModels() {
        try {
            JsonNode root = ollamaRestClient.get()
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);

            List<String> models = new ArrayList<>();
            if (root != null) {
                for (JsonNode item : root.path("models")) {
                    String name = item.path("name").asString();
                    if (name != null && !name.isBlank()) {
                        models.add(name);
                    }
                }
            }
            return models;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法连接本地 Ollama（" + e.getMessage()
                            + "）。请确认 Ollama 正在运行，且 app.ai.base-url 指向正确。", e);
        }
    }
}
