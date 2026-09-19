package com.yan.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池。
 *
 * <p>两个池子各管一摊，配置策略不同：
 * <ul>
 *   <li>{@code logExecutor}：操作日志落库。宁可慢也不丢。</li>
 *   <li>{@code aiExecutor}：AI 故障分析。宁可丢弃也不阻塞用户请求。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("logExecutor")
    public ThreadPoolTaskExecutor logExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("oper-log-");
        executor.setKeepAliveSeconds(60);

        // 队列满了不让它抛异常丢日志，而是退回调用线程同步执行。
        // 日志宁可慢一点，也不能丢 —— 否则审计记录就不完整了。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }

    /**
     * AI 分析专用线程池。
     *
     * <p>和日志池分开，而且配置策略刚好相反：
     * <ul>
     *   <li><b>池子极小</b>（1 个线程）：Ollama 跑在本机 CPU 上，并发调用只会互相抢算力，
     *       结果是每个都变慢。串行排队反而是最优的。</li>
     *   <li><b>队列满了直接丢弃</b>，不像日志池那样退回调用线程执行。
     *       因为一次分析要十几秒，让调用线程（那是 HTTP 请求线程）去跑，
     *       等于把用户卡住 —— 而这里调用方是事务提交后的回调，卡住的是建单请求。
     *       丢掉的后果只是工单停在"待分析"，用户可以点"重新分析"，代价小得多。</li>
     * </ul>
     */
    @Bean("aiExecutor")
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-analyze-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler((r, pool) ->
                log.warn("AI 分析任务队列已满，本次分析被丢弃。工单会停在「待分析」，可手动重新分析。"));
        executor.initialize();
        return executor;
    }
}
