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
        // 演示用户要排在 RBAC 之后（它依赖 operator 角色），
        // 排在演示工单/维保之前（那些数据的负责人要用这些账号当收件人）
        seedSafely("演示用户", seeder::seedDemoUsers);
        // 菜单与权限点：**每次启动都跑**，靠逐条判断做幂等。
        // 不能加"已经有就整体跳过"的粗粒度开关，否则以后新增的权限点
        // 在老库上永远补不上（这个坑踩过两次了）
        seedSafely("菜单与权限点", seeder::seedMenusAndPerms);
        // 业务模块的权限点。同样每次启动都跑，靠逐条判断做幂等
        seedSafely("业务模块权限点", seeder::seedBusinessMenusAndPerms);
        // 系统参数与字典：都要排在依赖它们的种子数据之前 ——
        // 演示工单要用「故障类型」字典，报修/看板要读参数
        seedSafely("系统参数", seeder::seedSystemConfigs);
        seedSafely("字典数据", seeder::seedDictionaries);
        // 部门要排在演示设备前面 —— 下面挂设备时得先有部门 id 可查。
        // 它本身也是逐条幂等的，老库上照样能补齐
        seedSafely("部门树", seeder::seedDepartments);
        seedSafely("设备分类", seeder::seedDeviceCategories);
        seedSafely("演示设备", seeder::seedDemoDevices);
        // 维保计划要排在演示设备之后 —— 计划是挂在设备上的，得先有设备
        seedSafely("演示维保计划", seeder::seedDemoMaintenance);
        seedSafely("演示配件", seeder::seedDemoSpareParts);
        // 工单要排在设备之后 —— 工单挂在设备上，而且会把设备状态改成"维修中"
        seedSafely("演示维修工单", seeder::seedDemoRepairs);
        // 数据修补必须放在最后：它负责回填 ddl-auto=update 新增列留下的 NULL，
        // 得等前面几块把数据都插完了才有意义
        seedSafely("历史数据修补", seeder::repairExistingData);
        // 初始密码标记。放在修补之后，因为要读已经建好的账号
        seedSafely("初始密码标记", seeder::repairDefaultPasswords);
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
