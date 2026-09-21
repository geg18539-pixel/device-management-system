package com.yan.backend.ai.provider;

import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
import java.util.Map;
import java.util.function.Consumer;

/**
 * 任意 **OpenAI 兼容**服务。
 *
 * <p>一套实现覆盖一大片：DeepSeek、通义千问（兼容模式）、Kimi、智谱、
 * 硅基流动、vLLM、LM Studio、one-api 之类的网关，以及 **Ollama 自己的
 * {@code /v1} 兼容端点**。它们的共同点就是 {@code /v1/chat/completions}
 * 和 {@code /v1/embeddings} 这两个路径。
 *
 * <h3>⚠️ 和 Ollama 原生接口的三个关键差异（写错都会静默出错）</h3>
 *
 * <ol>
 *   <li><b>流式格式是 SSE，不是 NDJSON。</b> 每行长成 {@code data: {...}}，
 *       以一个 {@code data: [DONE]} 结束。按 NDJSON 解析会一行都读不出来，
 *       表现成"模型一个字都不说"。</li>
 *   <li><b>工具调用的 {@code arguments} 是分片的 <u>字符串</u></b>，
 *       要按 index 把片段拼起来才是完整 JSON。而且 {@code id} 只在**第一片**里出现，
 *       必须留着 —— 后面回填工具结果时要用它配对（Ollama 用的是 tool_name，
 *       这是两家最容易搞混的地方）。</li>
 *   <li><b>结构化输出各家支持程度差别极大。</b> OpenAI 有 {@code json_schema}，
 *       DeepSeek 只有 {@code json_object}，还有不少自建服务完全不认这个字段
 *       而且**会直接 400**。所以这里先带 {@code json_object} 试一次，
 *       遇到"不认这个参数"的报错就**去掉它重试** —— 反正调用方本来就要
 *       从文本里再抠一次 JSON（见 {@link LlmProvider#structured} 的约定）。</li>
 * </ol>
 *
 * <h3>⚠️ {@code think} 这个参数在这里被<u>有意忽略</u></h3>
 *
 * <p>{@code think} 是 <b>Ollama 原生接口的特性</b>（见 {@link ChatRequest#think}），
 * OpenAI 兼容协议里没有这个字段，所以这个实现**根本不读它**。
 *
 * <p>后果要说清楚：把提供方从 ollama 换成 openai（比如换成通义千问的兼容模式）之后，
 * 「先思考再回答」这个开关就**失效了** —— 而它是会生效还是失效，界面上看不出来。
 * 各家其实都有自己的写法（通义是 {@code enable_thinking}、DeepSeek 是
 * {@code reasoning_content} 那一套），但各不相同，等真有人需要时再按家适配，
 * 不要在这里拍脑袋塞一个猜的名字 —— 那只会变成第二种静默失效。
 */
@Component
public class OpenAiLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmProvider.class);

    /**
     * ⚠️ 路径里**不含 {@code /v1}** —— 版本段由配置的 base-url 带上。
     *
     * <p>这是 OpenAI SDK 的约定（{@code base_url} 一般是
     * {@code https://api.openai.com/v1}），也是各家文档给的写法。
     * 一开始我把 {@code /v1} 写死在这里，结果国内几家常被选用的服务全都对不上：
     * <pre>
     *   通义千问  https://dashscope.aliyuncs.com/compatible-mode/v1
     *   Kimi      https://api.moonshot.cn/v1
     *   硅基流动  https://api.siliconflow.cn/v1
     * </pre>
     * 它们本身就是带 {@code /v1} 的，再拼一次就变成 {@code .../v1/v1/chat/completions} → 404。
     * 而智谱用的是 {@code /api/paas/v4}，更不可能靠猜补出来。
     * 所以版本段交给配置，这里只拼资源路径。
     */
    private static final String CHAT_PATH = "/chat/completions";
    private static final String MODELS_PATH = "/models";

    private final AiSettingsService settings;
    private final AiHttpClients clients;
    private final ObjectMapper objectMapper;

    public OpenAiLlmProvider(AiSettingsService settings,
                             AiHttpClients clients,
                             ObjectMapper objectMapper) {
        this.settings = settings;
        this.clients = clients;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return AiSettingsService.PROVIDER_OPENAI;
    }

    @Override
    public String label() {
        return "OpenAI 兼容服务";
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModel(request.model()));
        payload.put("messages", toOpenAiMessages(request.messages()));
        payload.put("stream", true);
        payload.put("temperature", request.temperature());
        payload.put("max_tokens", request.maxTokens());
        if (request.hasTools()) {
            payload.put("tools", request.tools());
        }

        StringBuilder text = new StringBuilder();
        // 按 index 归并：arguments 是分片到达的，id 只在第一片里
        Map<Integer, Map<String, Object>> callsByIndex = new LinkedHashMap<>();

        try {
            postStream(CHAT_PATH, payload)
                    .exchange((req, res) -> relaySse(res.getBody(), onText, text, callsByIndex));
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
     * 解析 SSE。
     *
     * <p>每一行长成 {@code data: {...}}，空行是事件分隔，
     * 最后一行是 {@code data: [DONE]}。
     */
    private Object relaySse(InputStream body, Consumer<String> onText,
                            StringBuilder text, Map<Integer, Map<String, Object>> callsByIndex) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // 空行是 SSE 的事件分隔符；以 ':' 开头的是注释（有些网关会发心跳）
                if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                    continue;
                }
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String data = trimmed.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode node;
                try {
                    node = objectMapper.readTree(data);
                } catch (Exception parseError) {
                    log.debug("跳过无法解析的 SSE 行: {}", data);
                    continue;
                }

                JsonNode delta = node.path("choices").path(0).path("delta");

                String chunk = delta.path("content").asString();
                if (chunk != null && !chunk.isEmpty()) {
                    text.append(chunk);
                    onText.accept(chunk);
                }

                JsonNode toolCalls = delta.path("tool_calls");
                if (toolCalls.isArray()) {
                    for (JsonNode tc : toolCalls) {
                        int index = tc.path("index").asInt(callsByIndex.size());
                        Map<String, Object> acc = callsByIndex.computeIfAbsent(
                                index, k -> new LinkedHashMap<>());

                        // id 只在第一片里出现
                        String id = tc.path("id").asString();
                        if (StringUtils.hasText(id)) {
                            acc.put("id", id);
                        }
                        JsonNode fn = tc.path("function");
                        String name = fn.path("name").asString();
                        if (StringUtils.hasText(name)) {
                            acc.put("name", name);
                        }
                        String args = fn.path("arguments").asString();
                        if (args != null && !args.isEmpty()) {
                            // ⚠️ 拼字符串，不是拼对象。中途的片段很可能不是合法 JSON，
                            // 边拼边解析必然失败 —— 只能等到全部收完再解析
                            String previous = (String) acc.getOrDefault("arguments", "");
                            acc.put("arguments", previous + args);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取模型输出失败: " + ex.getMessage(), ex);
        }
        return null;
    }

    private List<ToolCall> toToolCalls(Map<Integer, Map<String, Object>> callsByIndex) {
        List<ToolCall> result = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> entry : callsByIndex.entrySet()) {
            Map<String, Object> acc = entry.getValue();
            String name = (String) acc.get("name");
            if (!StringUtils.hasText(name)) {
                // 只收到片段、没收到名字，说明这个调用不完整，丢掉比瞎猜好
                continue;
            }
            String id = (String) acc.get("id");
            if (!StringUtils.hasText(id)) {
                // 少数服务不发 id，本地补一个保证能配对
                id = "openai-call-" + entry.getKey();
            }
            String arguments = (String) acc.getOrDefault("arguments", "{}");
            result.add(new ToolCall(id, name, arguments.isBlank() ? "{}" : arguments));
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
        payload.put("messages", toOpenAiMessages(request.messages()));
        payload.put("temperature", request.temperature());
        payload.put("max_tokens", request.maxTokens());
        // 先试 json_object：它比完整的 json_schema 普及得多
        payload.put("response_format", Map.of("type", "json_object"));

        try {
            return doStructured(payload);
        } catch (Exception ex) {
            if (!looksLikeResponseFormatRejected(ex)) {
                throw wrap(ex);
            }
            // 这一家不认 response_format（有些自建服务会直接 400）。
            // 去掉它重试 —— 调用方本来就要从文本里再抠一次 JSON，
            // 所以"没约束"只是成功率低一点，不该让整个功能挂掉
            log.warn("提供方不接受 response_format，去掉该参数重试：{}", ex.getMessage());
            payload.remove("response_format");
            try {
                return doStructured(payload);
            } catch (Exception retryEx) {
                throw wrap(retryEx);
            }
        }
    }

    private String doStructured(Map<String, Object> payload) {
        JsonNode response = client().post()
                .uri(CHAT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(this::applyAuth)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            return "";
        }
        return response.path("choices").path(0).path("message").path("content").asString();
    }

    /** 判断报错是不是"不认 response_format"这一类 */
    private boolean looksLikeResponseFormatRejected(Exception ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return message.contains("response_format")
                || message.contains("json_object")
                || message.contains("json_schema");
    }

    // ============================================================
    // 模型列表
    // ============================================================

    @Override
    public List<String> listModels() {
        try {
            // 一条链写完，不抽中间变量：RestClient.get() 返回的是通配类型
            // （RequestHeadersUriSpec<?>），一路链下去每一步都是"捕获的 ?"，
            // 赋给具体类型会编译不过
            JsonNode root = client().get()
                    .uri(MODELS_PATH)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuth)
                    .retrieve()
                    .body(JsonNode.class);
            List<String> models = new ArrayList<>();
            if (root != null) {
                for (JsonNode item : root.path("data")) {
                    String id = item.path("id").asString();
                    if (StringUtils.hasText(id)) {
                        models.add(id);
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

    /**
     * 转成 OpenAI 的消息格式。
     *
     * <p>和 Ollama 的两处不同：工具结果的字段是 {@code tool_call_id}，
     * 而 assistant 的 {@code arguments} 直接就是字符串（我们的中性表示本来就是这个）。
     */
    private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role());

            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : m.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", call.name());
                    fn.put("arguments", call.argumentsJson());
                    calls.add(Map.of("id", call.id(), "type", "function", "function", fn));
                }
                msg.put("tool_calls", calls);
                // content 可以为 null，但显式给空串兼容性更好
                msg.put("content", m.content() == null ? "" : m.content());
            } else if ("tool".equals(m.role())) {
                msg.put("tool_call_id", m.toolCallId());
                msg.put("content", m.content());
            } else {
                msg.put("content", m.content() == null ? "" : m.content());
            }
            out.add(msg);
        }
        return out;
    }

    private RestClient.RequestBodySpec post(String path, Map<String, Object> payload) {
        return client().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(this::applyAuth)
                .body(payload);
    }

    private RestClient.RequestHeadersSpec<?> postStream(String path, Map<String, Object> payload) {
        return client().post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .headers(this::applyAuth)
                .body(payload);
    }

    /**
     * 带上鉴权头。
     *
     * <p>密钥为空时**不加这个头** —— 本机跑 LM Studio / vLLM 这类服务不需要鉴权，
     * 硬塞一个 {@code Bearer } 反而可能被拒。
     *
     * <p>用 {@code headers(Consumer)} 而不是 {@code header(k, v)}：
     * 后者在通配泛型上会返回捕获类型，赋值时得强转，写出来很难看。
     */
    private void applyAuth(org.springframework.http.HttpHeaders headers) {
        String apiKey = settings.chatApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.set("Authorization", "Bearer " + apiKey.trim());
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
     * 把底层异常翻译成一句能照着做的话。
     *
     * <p>**密钥绝不回显**，只提示"配没配"。
     */
    private IllegalStateException wrap(Exception ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        String hint = StringUtils.hasText(settings.chatApiKey())
                ? ""
                : "（当前没有配置 API Key，如果是需要鉴权的服务，请设置环境变量 AI_CHAT_API_KEY）";
        return new IllegalStateException(
                "调用对话模型失败（提供方：OpenAI 兼容，地址：" + settings.chatBaseUrl()
                        + "，实际请求 " + settings.chatBaseUrl() + CHAT_PATH + "）"
                        + hint + "。地址错了的话请核对：base-url 要**填到版本段为止**，"
                        + "例如 https://api.deepseek.com/v1、https://api.moonshot.cn/v1。"
                        + "原始错误：" + detail, ex);
    }
}
