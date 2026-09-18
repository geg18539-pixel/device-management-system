package com.yan.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 应用入口。
 *
 * <p>@EnableAsync 打开异步方法支持，操作日志的落库（LogAspect 里的
 * @Async 方法）依赖它。不加这个注解，@Async 会被静默忽略 ——
 * 方法照常执行，但变成同步的，日志写库会拖慢每个接口的响应。
 */
@EnableAsync
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
