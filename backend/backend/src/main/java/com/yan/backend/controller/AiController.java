package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.dto.AiChatRequest;
import com.yan.backend.dto.AiModelsVO;
import com.yan.backend.service.AiChatService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 本地 AI 助手（对接 Ollama）。
 *
 * <p>路径在 {@code /api/**} 下，所以和其他接口一样需要登录 —— 由 JwtInterceptor 统一把关。
 *
 * <p><b>这里刻意没有加 @Log 注解。</b>
 * 聊天方法是"立刻返回一个 StreamingResponseBody"的：真正调用模型、读取流、
 * 写响应这些事都发生在方法返回**之后**的异步线程里。
 * 如果加 @Log，切面记录到的耗时几乎为 0、状态永远是"成功"，
 * 哪怕模型根本没连上 —— 那是一条会误导人的审计记录，还不如不记。
 * 真正的调用情况在 AiChatServiceImpl 的日志里（开始/结束/耗时/失败原因）。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
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
     * GET /api/ai/models —— 列出 Ollama 已安装的模型。
     *
     * <p>顺带充当连通性检查：前端进页面时调一次，
     * 拿不到就说明 Ollama 没启动，可以直接给出提示而不是等用户发消息才报错。
     */
    @GetMapping("/models")
    public ResponseEntity<Result<AiModelsVO>> models() {
        List<String> models = aiChatService.listModels();
        return ResponseEntity.ok(Result.success(
                new AiModelsVO(models, aiChatService.getDefaultModel())));
    }
}
