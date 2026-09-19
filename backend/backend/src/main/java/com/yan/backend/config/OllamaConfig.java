package com.yan.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 访问本地 Ollama 的 HTTP 客户端。
 *
 * <p>用 RestClient 而不是 WebClient：前者是 Spring 6.1 起提供的同步客户端，
 * 就在 spring-web 里，不需要额外引入 webflux。
 */
@Configuration
public class OllamaConfig {

    @Bean
    public RestClient ollamaRestClient(@Value("${app.ai.base-url}") String baseUrl,
                                       @Value("${app.ai.connect-timeout-seconds:10}") long connectTimeoutSeconds,
                                       @Value("${app.ai.read-timeout-minutes:5}") long readTimeoutMinutes) {

        // 关键：必须用 JdkClientHttpRequestFactory，不能用 SimpleClientHttpRequestFactory。
        // 后者底层是 HttpURLConnection，有把整个响应体缓冲下来再返回的倾向，
        // 那样流式输出会退化成"一次性返回" —— 表面能跑，但一个字都不会提前出来。
        // JdkClientHttpRequestFactory 底层是 java.net.http.HttpClient，逐块读取是可靠的。
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // 读超时给得比较长：3B 模型在 CPU 上生成一段回答可能要几十秒，
        // 用默认的十几秒会在生成到一半时被掐断。
        requestFactory.setReadTimeout(Duration.ofMinutes(readTimeoutMinutes));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
