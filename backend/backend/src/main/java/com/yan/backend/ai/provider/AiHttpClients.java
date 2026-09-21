package com.yan.backend.ai.provider;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 baseUrl 缓存 HTTP 客户端。
 *
 * <p>为什么需要缓存：提供方地址现在是**运行时可变配置**（管理员在系统设置里改），
 * 所以不能像以前那样在启动时建一个写死 baseUrl 的 bean。
 * 但也不能每次请求都新建 —— 每次新建意味着每个请求一个连接池，
 * 既浪费又会让 TCP 连接反复建立。
 *
 * <p>缓存键是 (baseUrl, 读超时)，因为这两者一起决定客户端的行为。
 * 地址改了自然会命中新的键，旧的留在 map 里也无所谓（个数等于改过的次数，很少）。
 *
 * <p><b>⚠️ baseUrl 要**填到版本段为止**</b>（{@code https://api.deepseek.com/v1}），
 * 提供方只负责往后拼 {@code /chat/completions} 这类资源路径。
 * 这是 OpenAI SDK 的约定，也是国内几家常被选用的服务（通义、Kimi、硅基流动）
 * 文档里给的写法 —— 它们的 base 本身就带 {@code /v1}，再拼一次就是 404。
 *
 * <p>⚠️ 必须用 {@link JdkClientHttpRequestFactory}，不能用
 * {@code SimpleClientHttpRequestFactory}。后者底层是 HttpURLConnection，
 * 有把整个响应体缓冲下来再返回的倾向 —— 那样流式输出会退化成"一次性返回"，
 * 这是本项目实测过的坑（见 AiController 的注释）。
 */
@Component
public class AiHttpClients {

    private final Map<String, RestClient> cache = new ConcurrentHashMap<>();

    public RestClient forBaseUrl(String baseUrl, Duration readTimeout) {
        String normalized = normalize(baseUrl);
        String key = normalized + "|" + readTimeout.toSeconds();
        return cache.computeIfAbsent(key, k -> build(normalized, readTimeout));
    }

    private RestClient build(String baseUrl, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 去掉结尾的斜杠。
     *
     * <p>配置里写成 {@code https://api.deepseek.com/} 的话，
     * 拼上 {@code /v1/chat/completions} 会变成双斜杠 —— 大多数服务能容忍，
     * 但也有不认的，统一在这里收口。
     */
    private String normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("AI 提供方的地址没有配置（app.ai.*.base-url）");
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
