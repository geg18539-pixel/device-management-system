package com.yan.backend.config;

import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.SysMenu;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开发环境初始数据。
 *
 * <p>三部分数据**各自独立判断是否需要初始化**，不是共用一个总开关。
 * 这一点很重要：如果像原来那样"sys_user 表非空就整体 return"，
 * 那么在一个已经跑过一段时间、sys_user 里已有账号的库上，
 * 后来新增的分类/设备种子数据就永远不会执行。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository sysUserRepository,
                           SysRoleRepository sysRoleRepository,
                           SysMenuRepository sysMenuRepository,
                           DeviceCategoryRepository deviceCategoryRepository,
                           DeviceRepository deviceRepository,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.sysMenuRepository = sysMenuRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initRbacData();
        initDeviceCategories();
        initDemoDevices();
    }

    // ============================================================
    // 1. RBAC：管理员账号、角色、菜单
    // ============================================================
    private void initRbacData() {
        if (sysUserRepository.count() > 0) {
            log.info("sys_user 表已有数据，跳过 RBAC 初始化");
            return;
        }
        log.info("检测到空库，开始初始化 RBAC 基础数据...");

        SysMenu deviceMenu = createMenu("设备管理", SysMenu.ROOT_PARENT_ID, "M",
                "/devices", null, "device:list", "Monitor", 1);
        SysMenu helloMenu = createMenu("联调测试", SysMenu.ROOT_PARENT_ID, "M",
                "/hello", null, "hello:view", "Link", 2);
        sysMenuRepository.saveAll(List.of(deviceMenu, helloMenu));

        SysMenu systemMenu = createMenu("系统管理", SysMenu.ROOT_PARENT_ID, "M",
                "/system", null, null, "Setting", 9);
        sysMenuRepository.save(systemMenu);

        SysMenu userMenu = createMenu("用户管理", systemMenu.getId(), "F",
                "/system/users", null, "sys:user:list", "User", 1);
        SysMenu roleMenu = createMenu("角色管理", systemMenu.getId(), "F",
                "/system/roles", null, "sys:role:list", "Avatar", 2);
        SysMenu menuMenu = createMenu("菜单管理", systemMenu.getId(), "F",
                "/system/menus", null, "sys:menu:list", "Menu", 3);
        sysMenuRepository.saveAll(List.of(userMenu, roleMenu, menuMenu));

        // 先存角色再存引用它的用户：@ManyToMany 没配 cascade，
        // 拿瞬时态的角色去存用户会报错
        SysRole adminRole = new SysRole();
        adminRole.setRoleName("超级管理员");
        adminRole.setRoleKey("admin");
        adminRole.setSortOrder(1);
        adminRole.setStatus("正常");
        adminRole.setRemark("拥有全部权限");
        adminRole.setMenus(new HashSet<>(List.of(
                deviceMenu, helloMenu, systemMenu, userMenu, roleMenu, menuMenu)));
        sysRoleRepository.save(adminRole);

        SysRole operatorRole = new SysRole();
        operatorRole.setRoleName("普通操作员");
        operatorRole.setRoleKey("operator");
        operatorRole.setSortOrder(2);
        operatorRole.setStatus("正常");
        operatorRole.setRemark("只能查看设备与联调页，看不到系统管理");
        // 只给前两个菜单，这样两个角色的侧边栏会真正不同
        operatorRole.setMenus(new HashSet<>(List.of(deviceMenu, helloMenu)));
        sysRoleRepository.save(operatorRole);

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setEmail("admin@example.com");
        admin.setStatus("正常");
        Set<SysRole> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);
        admin.setRoles(adminRoles);
        sysUserRepository.save(admin);

        SysUser operator = new SysUser();
        operator.setUsername("operator");
        operator.setPassword(passwordEncoder.encode("operator123"));
        operator.setNickname("普通操作员");
        operator.setStatus("正常");
        Set<SysRole> operatorRoles = new HashSet<>();
        operatorRoles.add(operatorRole);
        operator.setRoles(operatorRoles);
        sysUserRepository.save(operator);

        log.info("RBAC 初始数据完成：admin/admin123（超管）、operator/operator123（普通）");
    }

    // ============================================================
    // 2. 设备分类树
    // ============================================================
    private void initDeviceCategories() {
        if (deviceCategoryRepository.count() > 0) {
            log.info("device_category 表已有数据，跳过分类初始化");
            return;
        }
        log.info("开始初始化设备分类...");

        // 顶级分类必须先 save 拿到 id，子分类才能把 parentId 指过去
        DeviceCategory production = saveCategory("生产设备", DeviceCategory.ROOT_PARENT_ID, 1, "车间生产相关");
        DeviceCategory network = saveCategory("网络设备", DeviceCategory.ROOT_PARENT_ID, 2, "机房网络相关");
        DeviceCategory office = saveCategory("办公设备", DeviceCategory.ROOT_PARENT_ID, 3, null);

        saveCategory("传感器", production.getId(), 1, null);
        saveCategory("执行器", production.getId(), 2, null);
        saveCategory("交换机", network.getId(), 1, null);
        saveCategory("路由器", network.getId(), 2, null);

        log.info("设备分类初始化完成（3 个顶级 + 4 个子分类）");
    }

    // ============================================================
    // 3. 演示设备
    // ============================================================
    private void initDemoDevices() {
        if (deviceRepository.count() > 0) {
            log.info("device 表已有数据，跳过演示设备初始化");
            return;
        }
        log.info("开始初始化演示设备...");

        // 分类 id 按名称查出来，避免依赖自增 id 的具体数值
        Map<String, Long> categoryIds = new HashMap<>();
        for (DeviceCategory category : deviceCategoryRepository.findAll()) {
            categoryIds.put(category.getCategoryName(), category.getId());
        }

        // 刻意让状态和分类分布不均匀，这样前端两个图表一打开就有明显形状
        saveDevice("温度传感器 A", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0001", "SN-T-001", Device.STATUS_ONLINE, "一号车间");
        saveDevice("温度传感器 B", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0002", "SN-T-002", Device.STATUS_ONLINE, "二号车间");
        saveDevice("压力传感器", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0003", "SN-P-001", Device.STATUS_OFFLINE, "一号车间");
        saveDevice("电动阀门", "执行器", categoryIds.get("执行器"),
                "ZC-2026-0004", "SN-V-001", Device.STATUS_REPAIRING, "三号车间");
        saveDevice("车间交换机", "交换机", categoryIds.get("交换机"),
                "ZC-2026-0005", "SN-SW-01", Device.STATUS_ONLINE, "机房");
        saveDevice("核心路由器", "路由器", categoryIds.get("路由器"),
                "ZC-2026-0006", "SN-RT-01", Device.STATUS_ONLINE, "机房");
        saveDevice("激光打印机", "打印机", categoryIds.get("办公设备"),
                "ZC-2026-0007", "SN-PR-01", Device.STATUS_OFFLINE, "行政办公室");
        // 这一台故意不填分类，用来验证图表里"未分类"那一项
        saveDevice("备用终端", null, null,
                "ZC-2026-0008", "SN-TM-01", Device.STATUS_ONLINE, "仓库");

        log.info("演示设备初始化完成（共 8 台，分布在 4 种状态、含 1 台未分类）");
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private SysMenu createMenu(String menuName, Long parentId, String menuType,
                               String path, String component, String perms,
                               String icon, int sortOrder) {
        SysMenu menu = new SysMenu();
        menu.setMenuName(menuName);
        menu.setParentId(parentId);
        menu.setMenuType(menuType);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setPerms(perms);
        menu.setIcon(icon);
        menu.setSortOrder(sortOrder);
        menu.setVisible(1);
        return menu;
    }

    private DeviceCategory saveCategory(String name, Long parentId, int sortOrder, String remark) {
        DeviceCategory category = new DeviceCategory();
        category.setCategoryName(name);
        category.setParentId(parentId);
        category.setSortOrder(sortOrder);
        category.setRemark(remark);
        return deviceCategoryRepository.save(category);
    }

    private void saveDevice(String name, String deviceType, Long categoryId,
                            String assetCode, String serialNumber, String status, String location) {
        Device device = new Device();
        device.setDeviceName(name);
        device.setDeviceType(deviceType);
        device.setCategoryId(categoryId);
        device.setAssetCode(assetCode);
        device.setSerialNumber(serialNumber);
        device.setStatus(status);
        device.setLocation(location);
        device.setPurchaseDate(LocalDate.now().minusMonths(10));
        device.setWarrantyDate(LocalDate.now().plusMonths(14));
        device.setDescription("初始化的演示数据");
        deviceRepository.save(device);
    }
}
