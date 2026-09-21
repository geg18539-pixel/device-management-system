package com.yan.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务。
 *
 * <p>单独一个类而不是在 {@code BackendApplication} 上加注解：
 * 启动类上堆太多功能注解之后，新人看到"这个应用还定时跑东西"会很意外。
 * 放在这里，涉及定时任务的东西一眼能找到。
 *
 * <p>目前只有一个定时任务：{@link com.yan.backend.schedule.MaintenanceDueNotifier}
 * （每天扫维保到期）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
