package com.yan.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时装载开发用的种子数据。
 *
 * <p>这里只负责**调度**：三块数据各自独立执行、独立捕获异常。
 *
 * <p><b>为什么要把异常吞掉、只打警告？</b>
 * 之前三块种子数据共用一个 {@code @Transactional}，任何一块失败
 * （比如某个列有 NOT NULL 约束而实体以为可以为空）都会让整个事务回滚，
 * 并且因为异常抛到了 {@code CommandLineRunner} 之外，
 * **Spring Boot 会直接判定启动失败并关停 Tomcat** ——
 * 表现就是"后端起不来、前端代理报 ECONNREFUSED"，而真正的原因只是
 * 一句开发用的演示数据没插进去，排查起来很绕。
 *
 * <p>种子数据是开发便利性的东西，不该有让整个应用起不来的权力。
 * 现在改成每块各自 try/catch：失败的只在日志里留一条 WARN，
 * 其他块照常完成，应用正常运行。真出问题看日志里的 "种子数据" 关键字即可。
 *
 * <p>注意：真正执行写库的是 {@link SystemDataSeeder}，一个独立的 bean。
 * 不能把这些 @Transactional 方法写在本类里自己调 —— 那是类内部自调用，
 * 不经过 Spring 代理，事务不会生效。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SystemDataSeeder seeder;

    public DataInitializer(SystemDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(String... args) {
        seedSafely("RBAC 基础数据", seeder::seedRbacData);
        seedSafely("设备分类", seeder::seedDeviceCategories);
        seedSafely("演示设备", seeder::seedDemoDevices);
    }

    /**
     * 执行一块种子数据，失败只记录不抛出。
     *
     * @param name 数据块名称，用于日志定位
     * @param task 实际执行的任务
     */
    private void seedSafely(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            // 只到 WARN 不到 ERROR：这是开发用的种子数据没装上，
            // 不影响应用本身的启动和已有功能
            log.warn("种子数据[{}]初始化失败，已跳过（不影响应用启动）。原因: {}",
                    name, e.getMessage());
            log.warn("种子数据[{}]失败明细:", name, e);
        }
    }
}
