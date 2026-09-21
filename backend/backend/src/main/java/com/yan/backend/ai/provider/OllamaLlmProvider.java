package com.yan.backend.ai.provider;

import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 本机 / 自建的 Ollama。
 *
 * <p>用的是它的**原生接口**（{@code /api/chat}），不是 OpenAI 兼容端点。
 * 原因：原生接口的 {@code format} 支持传**完整的 JSON Schema** 来做结构化输出，
 * 而兼容端点在这块的支持要弱一些；工具调用在原生接口上也更稳。
 * 反正 Ollama 是本项目的默认后端，值得为它多写一份。
 */
@Component
public class OllamaLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaLlmProvider.class);

    private final AiSettingsService settings;
    private final AiHttpClients clients;
    private final ObjectMapper objectMapper;

    public OllamaLlmProvider(AiSettingsService settings,
                             AiHttpClients clients,
                             ObjectMapper objectMapper) {
        this.settings = settings;
        this.clients = clients;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return AiSettingsService.PROVIDER_OLLAMA;
    }

    @Override
    public String label() {
        return "本机 Ollama";
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    // ============================================================
    // 流式对话
    // ============================================================

    @Override
    public LlmRoundResult streamRound(ChatRequest request, Consumer<String> onText) {
        Map<String, Object> payload = buildChatPayload(request);
        // Ollama 的 stream 默认就是 true，显式写出来更清楚。
        // 传 false 会拿到一个完整 JSON 而不是 NDJSON 流，逐行解析会一行都读不到
        payload.put("stream", true);
        if (request.hasTools()) {
            payload.put("tools", request.tools());
        }
        putThink(payload, request.think());

        // 记下"有没有真的往调用方写过东西"：已经发给下游的字节收不回来，
        // 那种情况下不能重试，否则用户会看到两遍开头
        boolean[] emitted = {false};
        Consumer<String> guarded = chunk -> {
            emitted[0] = true;
            onText.accept(chunk);
        };

        try {
            return doStreamRound(payload, guarded);
        } catch (Exception ex) {
            if (canRetryWithoutThink(ex, emitted[0], payload)) {
                log.warn("该服务不接受 think 参数，已去掉它重试一次：{}", ex.getMessage());
                return doStreamRound(payload, guarded);
            }
            throw wrap(ex);
        }
    }

    /** 真正跑一轮流式对话。抽出来是为了让"去掉 think 重试一次"能复用同一段代码 */
    private LlmRoundResult doStreamRound(Map<String, Object> payload, Consumer<String> onText) {
        StringBuilder text = new StringBuilder();
        // 按 index 归并：一次工具调用的 name 和 arguments 可能分散在不同分片里
        Map<Integer, Map<String, Object>> callsByIndex = new LinkedHashMap<>();

        try {
            client().post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((req, res) -> {
                        checkErrorStatus(res);
                        return relay(res.getBody(), onText, text, callsByIndex);
                    });
        } catch (Exception ex) {
            throw wrap(ex);
        }

        List<ToolCall> toolCalls = toToolCalls(callsByIndex);
        String content = text.toString();
        ChatMessage assistantMessage = toolCalls.isEmpty()
                ? ChatMessage.assistant(content)
                : ChatMessage.assistantToolCalls(content, toolCalls);
        return new LlmRoundResult(content, toolCalls, assistantMessage);
    }

    /**
     * 逐行读 Ollama 的 NDJSON。
     *
     * <p>文本分片边收边转发，同时把 tool_calls 累积下来 ——
     * 攒起来一起给的话，流式就退化成"等很久然后一次性出现"了。
     */
    private Object relay(InputStream body, Consumer<String> onText,
                         StringBuilder text, Map<Integer, Map<String, Object>> callsByIndex) {
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
                    // 单行解析失败不中断整轮，不然一个坏行会让整个回答空掉
                    log.debug("跳过无法解析的 NDJSON 行: {}", line);
                    continue;
                }
                JsonNode message = node.path("message");

                String chunk = message.path("content").asString();
                if (chunk != null && !chunk.isEmpty()) {
                    text.append(chunk);
                    onText.accept(chunk);
                }

                JsonNode toolCalls = message.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        JsonNode fn = tc.path("function");
                        // ⚠️ Ollama 的 index 在 function **里面**（不是外面），
                        // 这一点和 OpenAI 不同
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
                            // ⚠️ 这里把对象原样留着，最后统一转成字符串（见 toToolCalls）
                            function.put("arguments", fn.path("arguments"));
                        }
                        call.put("function", function);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取模型输出失败: " + ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * 把累积到的分片整理成统一的 {@link ToolCall}。
     *
     * <p>Ollama 的 {@code arguments} 本来就是**对象**，而统一约定是 JSON 字符串
     * （因为 OpenAI 那边给的是字符串），所以这里序列化一次。
     * 两边归一之后，工具执行器就不用关心自己连的是哪家了。
     */
    private List<ToolCall> toToolCalls(Map<Integer, Map<String, Object>> callsByIndex) {
        List<ToolCall> result = new ArrayList<>();
        int seq = 0;
        for (Map<String, Object> call : callsByIndex.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) call.get("function");
            if (function == null) {
                continue;
            }
            String name = function.get("name") == null ? null : String.valueOf(function.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            Object arguments = function.get("arguments");
            String argumentsJson = arguments == null
                    ? "{}"
                    : objectMapper.writeValueAsString(arguments);
            // Ollama 不返回调用 id。给一个本地生成的就够了 ——
            // 它只在"回填工具结果"时用来配对，而 Ollama 那边其实认的是 tool_name
            result.add(new ToolCall("ollama-call-" + (seq++), name, argumentsJson));
        }
        return result;
    }

    // ============================================================
    // 结构化输出
    // ============================================================

    @Override
    public String structured(StructuredRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModel(request.model()));
        payload.put("messages", toOllamaMessages(request.messages()));
        // 要完整 JSON，不要流式分片
        payload.put("stream", false);
        // format 传 JSON Schema，让 Ollama 约束输出结构。
        // 这是选原生接口的主要原因 —— 即使小模型判断水平有限，
        // 至少返回的 JSON 是合法且字段齐全的
        if (request.jsonSchema() != null) {
            payload.put("format", request.jsonSchema());
        }
        payload.put("options", Map.of(
                "temperature", request.temperature(),
                "num_predict", request.maxTokens()));
        putThink(payload, request.think());

        try {
            return doStructured(payload);
        } catch (Exception ex) {
            // 非流式，没有"已经写出去"的问题，可以放心重试
            if (canRetryWithoutThink(ex, false, payload)) {
                log.warn("该服务不接受 think 参数，已去掉它重试一次：{}", ex.getMessage());
                return doStructured(payload);
            }
            throw wrap(ex);
        }
    }

    private String doStructured(Map<String, Object> payload) {
        try {
            JsonNode response = client().post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? "" : response.path("message").path("content").asString();
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    // ============================================================
    // think 参数
    // ============================================================

    /**
     * HTTP 状态是 4xx/5xx 时抛一个**带上了响应体**的异常。
     *
     * <p>⚠️ 这一步是必须的，而且踩过一次：流式用的是
     * {@code RestClient.exchange()}，它**不会因为错误状态码抛异常** ——
     * 不自己检查的话，错误响应的那段 JSON（形如
     * {@code {"error":"..."}}）会被当成 NDJSON 逐行解析，
     * 一行都解析不出 content，最后表现为<b>"模型一个字都没说"</b>。
     *
     * <p>真实原因（参数不被支持、模型没拉下来、地址填错…）全被吞掉，
     * 用户看到的只有一句"模型没有返回任何内容"，完全没法排查。
     * 把响应体带上之后，日志和前端提示里就能看到服务端到底说了什么。
     */
    private void checkErrorStatus(ClientHttpResponse res) throws IOException {
        if (!res.getStatusCode().isError()) {
            return;
        }
        String detail = "";
        try (InputStream in = res.getBody()) {
            detail = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // 别把一整页 HTML 塞进异常消息里
            if (detail.length() > 500) {
                detail = detail.substring(0, 500) + "…";
            }
        } catch (Exception ignored) {
            // 读不出响应体不影响判断，只要知道它失败了就行
        }
        throw new IllegalStateException(
                "模型服务返回 " + res.getStatusCode().value() + "：" + detail);
    }

    /**
     * 按需把 {@code think} 放进请求体。
     *
     * <p><b>值为 null 时什么都不放</b>：老版本 Ollama 和不支持思考的模型
     * 收到这个字段可能直接报错，所以"不指定"和"指定为 false"是两件事。
     */
    private void putThink(Map<String, Object> payload, Boolean think) {
        if (think != null) {
            payload.put("think", think);
        }
    }

    /**
     * 这次失败是不是"服务端不认 think"造成的，能不能去掉它重试。
     *
     * <p>判据是异常链里出现 {@code think} 字样。**故意做得宽松**：
     * 误判的代价只是多发一次请求（去掉一个可选的字段而已），
     * 而漏判的代价是用户彻底用不了 —— 所以宁可误判。
     *
     * <p>{@code emitted} 为 true 时一律不重试：流式场景下已经发给下游的
     * 字节收不回来，重试会让用户看到两遍开头。
     */
    private boolean canRetryWithoutThink(Throwable ex, boolean emitted, Map<String, Object> payload) {
        if (emitted || !payload.containsKey("think")) {
            return false;
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("think")) {
                payload.remove("think");
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 模型列表
    // ============================================================

    @Override
    public List<String> listModels() {
        try {
            JsonNode root = client().get()
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
        } catch (Exception ex) {
            throw wrap(ex);
        }
    }

    // ============================================================
    // 内部
    // ============================================================

    private Map<String, Object> buildChatPayload(ChatRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModel(request.model()));
        payload.put("messages", toOllamaMessages(request.messages()));
        payload.put("options", Map.of(
                "temperature", request.temperature(),
                "num_predict", request.maxTokens()));
        return payload;
    }

    /**
     * 转成 Ollama 的消息格式。
     *
     * <p>两个和 OpenAI 不一样的地方：
     * <ul>
     *   <li>assistant 的工具调用里，{@code arguments} 要的是**对象**，
     *       而我们的中性表示里是字符串 → 要解回来；</li>
     *   <li>工具结果消息用 {@code tool_name} 而不是 {@code tool_call_id}。</li>
     * </ul>
     */
    private List<Map<String, Object>> toOllamaMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role());

            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                int index = 0;
                for (ToolCall call : m.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("index", index++);
                    fn.put("name", call.name());
                    // 字符串 → 对象。解不出来就给个空对象，总比整个请求失败好
                    fn.put("arguments", parseArguments(call.argumentsJson()));
                    calls.add(Map.of("type", "function", "function", fn));
                }
                msg.put("tool_calls", calls);
                msg.put("content", m.content() == null ? "" : m.content());
            } else if ("tool".equals(m.role())) {
                msg.put("tool_name", m.toolName());
                msg.put("content", m.content());
            } else {
                msg.put("content", m.content() == null ? "" : m.content());
            }
            out.add(msg);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception ex) {
            log.warn("工具参数不是合法 JSON，已按空对象处理：{}", argumentsJson);
            return Map.of();
        }
    }

    private String resolveModel(String requested) {
        return (requested == null || requested.isBlank()) ? settings.chatModel() : requested.trim();
    }

    private RestClient client() {
        return clients.forBaseUrl(settings.chatBaseUrl(),
                Duration.ofMinutes(Math.max(1, settings.chatReadTimeoutMinutes())));
    }

    /**
     * 把底层异常翻译成一句用户能照着做的话。
     *
     * <p>最常见的两种情况是"Ollama 没启动"和"模型没拉下来"，
     * 它们的处理动作完全不同，混成一句"调用失败"等于没说。
     */
    private IllegalStateException wrap(Exception ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String baseUrl = settings.chatBaseUrl();
        return new IllegalStateException(
                "调用对话模型失败（提供方：Ollama，地址：" + baseUrl + "）。"
                        + "请确认 Ollama 正在运行（命令行执行 ollama list 能列出模型）、"
                        + "要用的模型已经 pulled（ollama pull " + settings.chatModel() + "）、"
                        + "以及系统设置里的地址配置正确。原始错误：" + detail, ex);
    }
}
