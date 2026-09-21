package com.yan.backend.controller;

import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.EmbeddingProvider;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AiChatRequest;
import com.yan.backend.dto.AiModelsVO;
import com.yan.backend.dto.AiStatusVO;
import com.yan.backend.service.AiChatService;
import com.yan.backend.service.AiSettingsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手。
 *
 * <p>支持两种提供方：**本机 Ollama**（原生接口）和**任意 OpenAI 兼容服务**
 * （DeepSeek、通义千问兼容模式、Kimi、智谱、硅基流动、vLLM、LM Studio 等）。
 * 具体用哪一家由「系统设置 → AI 模型」决定，这里是提供方无关的。
 *
 * <p>路径在 {@code /api/**} 下，所以和其他接口一样需要登录 —— 由 JwtInterceptor 统一把关。
 *
 * <p><b>这里刻意没有加 @Log 注解。</b>
 * 聊天方法是"立刻返回"的：真正调用模型、读取流、写响应这些事都发生在
 * 方法返回**之后**。如果加 @Log，切面记录到的耗时几乎为 0、状态永远是"成功"，
 * 哪怕模型根本没连上 —— 那是一条会误导人的审计记录，还不如不记。
 * 真正的调用情况在 AiChatServiceImpl 的日志里（开始/结束/耗时/失败原因）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService aiChatService;
    private final AiProviderRegistry providerRegistry;
    private final AiSettingsService aiSettings;

    public AiController(AiChatService aiChatService,
                        AiProviderRegistry providerRegistry,
                        AiSettingsService aiSettings) {
        this.aiChatService = aiChatService;
        this.providerRegistry = providerRegistry;
        this.aiSettings = aiSettings;
    }

    /**
     * GET /api/ai/status —— 当前生效的 AI 配置摘要（脱敏）。
     *
     * <p>返回的是**生效值**而不是"库里的字面值"：界面上要显示的是实际在用哪一家、
     * 哪个地址、哪个模型。provider / base-url / model 这三项都可能是
     * "库里没配、跟着配置文件或环境变量走"，管理员更需要看到最终结果。
     *
     * <p>**不含 API Key**，只有一个"配没配"的布尔值。
     *
     * <p>不做探活：连不上是几秒到几十秒的事，不该让打开设置页变成一次等待。
     * 探活走 {@link #models(String)}。
     */
    @GetMapping("/status")
    public ResponseEntity<Result<AiStatusVO>> status() {
        AiStatusVO vo = new AiStatusVO();

        List<AiStatusVO.Option> options = new ArrayList<>();
        for (LlmProvider provider : providerRegistry.allLlm()) {
            options.add(new AiStatusVO.Option(provider.id(), provider.label()));
        }
        // 嵌入提供方的可选集和对话是同一批（两家都同时提供对话和嵌入），不另开一个列表
        vo.setProviders(options);

        LlmProvider chat = providerRegistry.currentLlm();
        AiStatusVO.ProviderStatus chatStatus = new AiStatusVO.ProviderStatus();
        chatStatus.setProvider(chat.id());
        chatStatus.setProviderLabel(chat.label());
        chatStatus.setBaseUrl(aiSettings.chatBaseUrl());
        chatStatus.setModel(aiSettings.chatModel());
        chatStatus.setApiKeyConfigured(hasText(aiSettings.chatApiKey()));
        chatStatus.setSupportsTools(chat.supportsTools());
        vo.setChat(chatStatus);

        EmbeddingProvider embedding = providerRegistry.currentEmbedding();
        AiStatusVO.ProviderStatus embeddingStatus = new AiStatusVO.ProviderStatus();
        embeddingStatus.setProvider(embedding.id());
        embeddingStatus.setProviderLabel(embedding.label());
        embeddingStatus.setBaseUrl(aiSettings.embeddingBaseUrl());
        embeddingStatus.setModel(embedding.modelName());
        embeddingStatus.setApiKeyConfigured(hasText(aiSettings.embeddingApiKey()));
        vo.setEmbedding(embeddingStatus);

        return ResponseEntity.ok(Result.success(vo));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * POST /api/ai/chat —— 流式对话。
     *
     * <p>返回 void、直接往 {@link HttpServletResponse} 里写，而不是返回
     * StreamingResponseBody。这是实测之后的选择，不是随手写的：
     *
     * <p>在本项目这套组合（Spring Boot 4.1.1 + Spring Framework 7.0.9 + Tomcat 11）
     * 上，我用 5 种写法做了对比测试（每次写出间隔 0.5 秒，看客户端收到字节的实际时刻）：
     * <pre>
     *   produces + StreamingResponseBody        → 2.53s 一次性到齐   ✗ 被缓冲
     *   去掉 produces + StreamingResponseBody   → 2.50s 一次性到齐   ✗ 被缓冲
     *   ResponseEntity&lt;StreamingResponseBody&gt;   → 2.51s 一次性到齐   ✗ 被缓冲
     *   直接写 HttpServletResponse               → 0.0/0.5/1.0/1.5/2.0/2.5 依次到达  ✓
     *   SseEmitter                              → 0.0/0.5/1.0/1.5/2.0/2.5 依次到达  ✓
     * </pre>
     * 也就是说 StreamingResponseBody 这条"最推荐"的路子在这里会退化成一次性返回 ——
     * 后端每 0.4 秒 flush 一次、日志时间戳完全正确，但字节直到响应结束才发给浏览器。
     * 所以这里用最朴素的方式：拿到 response 的输出流直接写，每次写完整理 flush。
     *
     * <p>不套统一的 Result 格式：流式和非流式在响应形态上本来就是两回事，
     * 硬包成 JSON 前端就没法逐块处理了。
     */
    @PostMapping("/chat")
    public void chat(@Valid @RequestBody AiChatRequest request,
                     HttpServletResponse response) throws IOException {

        // 中文必须声明编码，否则浏览器可能按 latin-1 解码，输出会变成乱码
        response.setContentType("text/plain;charset=UTF-8");

        // 告诉 nginx 这个响应不要缓冲（nginx 认这个头）。
        // nginx.conf 里也单独给 /api/ai/ 配了 proxy_buffering off，
        // 两处都做是为了防止有人改了 nginx 配置却忘了这里的语义。
        response.setHeader("X-Accel-Buffering", "no");

        aiChatService.streamChat(request.messages(), request.model(), response.getOutputStream());
    }

    /**
     * GET /api/ai/models —— 列出当前提供方下可用的模型。
     *
     * <p>顺带充当**连通性检查**：设置页的「测试连接」和 AI 助手页进页面时各调一次。
     * 拿不到就说明提供方连不上（或密钥不对），可以直接给出提示，
     * 而不是等用户发消息才报错。
     *
     * <p>{@code purpose=chat|embedding} 决定查哪一边 —— 两边可以接不同的提供方，
     * 所以要分别测。失败时抛出的异常里已经写清了是哪一家、哪个地址、
     * 要不要配 API Key，这里不吞不包。
     */
    @GetMapping("/models")
    public ResponseEntity<Result<AiModelsVO>> models(
            @RequestParam(defaultValue = "chat") String purpose) {
        boolean embedding = "embedding".equalsIgnoreCase(purpose);
        List<String> models = embedding
                ? providerRegistry.currentEmbedding().listModels()
                : providerRegistry.currentLlm().listModels();
        String current = embedding ? aiSettings.embeddingModel() : aiSettings.chatModel();
        return ResponseEntity.ok(Result.success(new AiModelsVO(models, current)));
    }
}
