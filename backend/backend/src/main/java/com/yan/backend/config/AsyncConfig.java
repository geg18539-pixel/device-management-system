package com.yan.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池。
 *
 * <p>给操作日志落库专用。用独立线程池而不是 Spring 的默认 executor，
 * 是为了避免日志写库把其他异步任务（比如以后的邮件、报表）挤在同一批线程里。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

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
}
