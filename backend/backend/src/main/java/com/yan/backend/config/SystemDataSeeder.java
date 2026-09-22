package com.yan.backend.config;

import com.yan.backend.common.ConfigKeys;
import com.yan.backend.common.DictTypes;
import com.yan.backend.common.PasswordPolicy;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceMaintenanceRecord;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;
import com.yan.backend.entity.SysConfig;
import com.yan.backend.entity.SysDept;
import com.yan.backend.entity.SysDictItem;
import com.yan.backend.entity.SysDictType;
import com.yan.backend.entity.SysMenu;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceMaintenancePlanRepository;
import com.yan.backend.repository.DeviceMaintenanceRecordRepository;
import com.yan.backend.repository.DeviceRepairLogRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SparePartRecordRepository;
import com.yan.backend.repository.SparePartRepository;
import com.yan.backend.repository.SysConfigRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.repository.SysDictItemRepository;
import com.yan.backend.repository.SysDictTypeRepository;
import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import com.yan.backend.service.SysConfigService;
import com.yan.backend.service.SysDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 开发环境的种子数据。
 *
 * <p><b>为什么单独抽成一个 bean？</b>
 * 因为 @Transactional 是基于 Spring 代理的：只有「从外部调用这个 bean 的方法」
 * 才会经过代理、真正开启事务。如果这些方法写在 {@link DataInitializer} 里
 * 被它自己直接调用，那是类内部自调用，**事务根本不生效** ——
 * 这样三块数据就会共用调用方的事务，任何一块失败都会把其他两块一起回滚。
 * 和 5.3 里 @Async 必须拆出去是同一个道理。
 *
 * <p>拆开之后每个方法各有独立事务，互不影响；再配合调用方的 try/catch，
 * 某块种子数据出问题只会打一条警告，应用照常启动。
 */
@Component
public class SystemDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(SystemDataSeeder.class);

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepository deviceRepository;
    private final SysDeptRepository sysDeptRepository;
    private final DeviceMaintenancePlanRepository maintenancePlanRepository;
    private final DeviceMaintenanceRecordRepository maintenanceRecordRepository;
    private final SparePartRepository sparePartRepository;
    private final SparePartRecordRepository sparePartRecordRepository;
    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepairLogRepository deviceRepairLogRepository;
    private final SysConfigRepository sysConfigRepository;
    private final SysDictTypeRepository sysDictTypeRepository;
    private final SysDictItemRepository sysDictItemRepository;
    private final SysConfigService sysConfigService;
    private final SysDictService sysDictService;
    private final PasswordEncoder passwordEncoder;

    public SystemDataSeeder(SysUserRepository sysUserRepository,
                            SysRoleRepository sysRoleRepository,
                            SysMenuRepository sysMenuRepository,
                            DeviceCategoryRepository deviceCategoryRepository,
                            DeviceRepository deviceRepository,
                            SysDeptRepository sysDeptRepository,
                            DeviceMaintenancePlanRepository maintenancePlanRepository,
                            DeviceMaintenanceRecordRepository maintenanceRecordRepository,
                            SparePartRepository sparePartRepository,
                            SparePartRecordRepository sparePartRecordRepository,
                            DeviceRepairRepository deviceRepairRepository,
                            DeviceRepairLogRepository deviceRepairLogRepository,
                            SysConfigRepository sysConfigRepository,
                            SysDictTypeRepository sysDictTypeRepository,
                            SysDictItemRepository sysDictItemRepository,
                            SysConfigService sysConfigService,
                            SysDictService sysDictService,
                            PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.sysMenuRepository = sysMenuRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepository = deviceRepository;
        this.sysDeptRepository = sysDeptRepository;
        this.maintenancePlanRepository = maintenancePlanRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.sparePartRepository = sparePartRepository;
        this.sparePartRecordRepository = sparePartRecordRepository;
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepairLogRepository = deviceRepairLogRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.sysDictTypeRepository = sysDictTypeRepository;
        this.sysDictItemRepository = sysDictItemRepository;
        this.sysConfigService = sysConfigService;
        this.sysDictService = sysDictService;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // 1. RBAC：管理员账号、角色、菜单
    // ============================================================
    @Transactional
    public void seedRbacData() {
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
    @Transactional
    public void seedDeviceCategories() {
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
    // 3. 部门树
    //
    // 和设备分类一样用「逐条判断」做幂等：每个部门自己检查"我这个
    // (名称 + 上级) 是否已存在"，不存在才插。
    // 这样以后往树里加新部门，在老库上也能自动补上。
    // ============================================================
    @Transactional
    public void seedDepartments() {
        log.info("开始初始化部门树...");

        // 顶级部门必须先 save 拿到 id，子部门才能把 parentId 指过去
        SysDept head = ensureDept("总公司", SysDept.ROOT_PARENT_ID, 1, "张建国", "010-88000000");

        SysDept production = ensureDept("生产中心", head.getId(), 1, "李伟", "010-88000001");
        ensureDept("一号车间", production.getId(), 1, "王强", "010-88000011");
        ensureDept("二号车间", production.getId(), 2, "赵敏", "010-88000012");
        ensureDept("三号车间", production.getId(), 3, "孙立", "010-88000013");

        SysDept tech = ensureDept("技术中心", head.getId(), 2, "陈静", "010-88000002");
        ensureDept("运维部", tech.getId(), 1, "周涛", "010-88000021");
        ensureDept("研发部", tech.getId(), 2, "吴磊", "010-88000022");

        SysDept admin = ensureDept("行政中心", head.getId(), 3, "刘芳", "010-88000003");
        ensureDept("行政部", admin.getId(), 1, "郑爽", "010-88000031");

        log.info("部门树初始化完成（总公司下 3 个中心、共 {} 个部门）",
                sysDeptRepository.count());
    }

    /**
     * 确保某个 (名称 + 上级) 的部门存在，返回它。
     *
     * <p>找不到就新建。这样重复启动、以及在已有数据的库上启动都是安全的。
     */
    private SysDept ensureDept(String name, Long parentId, int sortOrder,
                               String leader, String phone) {
        return sysDeptRepository.findByDeptNameAndParentId(name, parentId)
                .orElseGet(() -> {
                    SysDept dept = new SysDept();
                    dept.setDeptName(name);
                    dept.setParentId(parentId);
                    dept.setSortOrder(sortOrder);
                    dept.setLeader(leader);
                    dept.setPhone(phone);
                    dept.setStatus(SysDept.STATUS_NORMAL);
                    return sysDeptRepository.save(dept);
                });
    }

    /** 按部门名取 id，给演示设备挂部门用 */
    private Long deptIdByName(String name) {
        for (SysDept dept : sysDeptRepository.findAll()) {
            if (name.equals(dept.getDeptName())) {
                return dept.getId();
            }
        }
        return null;
    }

    // ============================================================
    // 4. 演示设备
    // ============================================================
    @Transactional
    public void seedDemoDevices() {
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

        // 部门同理，按名称查 id
        Long workshop1 = deptIdByName("一号车间");
        Long workshop2 = deptIdByName("二号车间");
        Long workshop3 = deptIdByName("三号车间");
        Long tech = deptIdByName("技术中心");
        Long adminDept = deptIdByName("行政部");

        // 刻意让状态和分类分布不均匀，这样前端两个图表一打开就有明显形状。
        //
        // 注意 deviceType 必须给值、不能传 null：实体上虽然已经把它改成了可选，
        // 但数据库里 device_type 这一列是旧版本建表时创建的、带 NOT NULL 约束，
        // 而 ddl-auto=update **只会新增表/列，不会放宽已有列的约束**，
        // 往这一列插 null 会被 MySQL 拒绝。想真正让这个字段可选，需要手工执行：
        //   ALTER TABLE device MODIFY COLUMN device_type VARCHAR(50) NULL;
        saveDevice("温度传感器 A", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0001", "SN-T-001", Device.STATUS_ONLINE, "一号车间",
                workshop1, "SHT-2000", "中科传感", Device.LIFECYCLE_NORMAL);
        saveDevice("温度传感器 B", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0002", "SN-T-002", Device.STATUS_ONLINE, "二号车间",
                workshop2, "SHT-2000", "中科传感", Device.LIFECYCLE_NORMAL);
        saveDevice("压力传感器", "传感器", categoryIds.get("传感器"),
                "ZC-2026-0003", "SN-P-001", Device.STATUS_OFFLINE, "一号车间",
                workshop1, "PRS-100", "中科传感", Device.LIFECYCLE_NORMAL);
        // 这台生命周期也设成"维修"，和它的连通状态"维修中"对应，
        // 这样看板上"维修中设备"这张卡片一打开就不是 0
        saveDevice("电动阀门", "执行器", categoryIds.get("执行器"),
                "ZC-2026-0004", "SN-V-001", Device.STATUS_REPAIRING, "三号车间",
                workshop3, "EV-300", "上海自动化", Device.LIFECYCLE_REPAIR);
        saveDevice("车间交换机", "交换机", categoryIds.get("交换机"),
                "ZC-2026-0005", "SN-SW-01", Device.STATUS_ONLINE, "机房",
                tech, "S5720", "华为", Device.LIFECYCLE_NORMAL);
        saveDevice("核心路由器", "路由器", categoryIds.get("路由器"),
                "ZC-2026-0006", "SN-RT-01", Device.STATUS_ONLINE, "机房",
                tech, "AR6300", "华为", Device.LIFECYCLE_NORMAL);
        saveDevice("激光打印机", "打印机", categoryIds.get("办公设备"),
                "ZC-2026-0007", "SN-PR-01", Device.STATUS_OFFLINE, "行政办公室",
                adminDept, "M405dw", "惠普", Device.LIFECYCLE_NORMAL);
        // 这一台故意不填分类（categoryId = null），用来验证图表里"未分类"那一项。
        // 注意这里为 null 的是 categoryId，不是 deviceType —— 那一列有 NOT NULL 约束。
        // 生命周期故意设成"报废"，让看板上报废统计不是恒为 0。
        saveDevice("备用终端", "终端", null,
                "ZC-2026-0008", "SN-TM-01", Device.STATUS_ONLINE, "仓库",
                null, "TC-100", "联想", Device.LIFECYCLE_SCRAPPED);

        log.info("演示设备初始化完成（共 8 台，分布在 4 种状态、含 1 台未分类）");
    }

    // ============================================================
    // 5. 数据修补（针对"已有库升级"场景）
    //
    // 这一节专门处理 ddl-auto=update 的固有短板：它只新增列，不回填数据。
    // 用户在升级到这一版时，库里已经有设备和用户了，所以：
    //   - device.lifecycle_status 全是 NULL（新加的列）
    //   - device.dept_id 全是 NULL（新加的列）
    // 不修补的话，看板上的"正常设备"会被拆成"正常"和"未知"两块，
    // 部门分布也会是一整块"未分配"。
    // ============================================================
    @Transactional
    public void repairExistingData() {
        // ---------- 生命周期状态回填 ----------
        // 顺序不能反：先把"维修中"的标成生命周期=维修，再把剩下的 NULL 标成正常
        int repairing = deviceRepository.backfillLifecycleByStatus(
                Device.STATUS_REPAIRING, Device.LIFECYCLE_REPAIR);
        int normal = deviceRepository.backfillNullLifecycleStatus(Device.LIFECYCLE_NORMAL);
        if (repairing > 0 || normal > 0) {
            log.info("生命周期状态回填完成：维修 {} 台、正常 {} 台", repairing, normal);
        }

        // ---------- 给演示设备补部门 ----------
        // 只处理"名称正好是演示数据那几台、且当前没有部门"的设备 ——
        // 用户自己新建的设备一台都不碰，避免把别人的数据改了。
        Map<String, String> demoDept = new LinkedHashMap<>();
        demoDept.put("温度传感器 A", "一号车间");
        demoDept.put("压力传感器", "一号车间");
        demoDept.put("温度传感器 B", "二号车间");
        demoDept.put("电动阀门", "三号车间");
        demoDept.put("车间交换机", "技术中心");
        demoDept.put("核心路由器", "技术中心");
        demoDept.put("激光打印机", "行政部");

        Map<String, Long> deptIds = new HashMap<>();
        for (SysDept dept : sysDeptRepository.findAll()) {
            deptIds.put(dept.getDeptName(), dept.getId());
        }

        int assigned = 0;
        for (Map.Entry<String, String> entry : demoDept.entrySet()) {
            Long deptId = deptIds.get(entry.getValue());
            if (deptId == null) {
                continue;
            }
            Device device = deviceRepository
                    .findFirstByDeviceNameAndDeptIdIsNullOrderByIdAsc(entry.getKey())
                    .orElse(null);
            if (device != null) {
                device.setDeptId(deptId);
                deviceRepository.save(device);
                assigned++;
            }
        }
        if (assigned > 0) {
            log.info("已给 {} 台演示设备补上归属部门（未触碰用户自建设备）", assigned);
        }
    }

    // ============================================================
    // 4. 系统菜单与权限点
    //
    // ★ 这里**刻意不做粗粒度开关**（比如"已经有按钮就整体跳过"）。
    //
    // 那种写法有个隐蔽的问题：以后新增一个权限点或菜单，在**已经跑过一段时间的库上
    // 永远加不进去** —— 开关一看"已经有按钮了"就直接 return 了。
    // 这个项目里同一类错误已经踩过两次：
    //   第一次是 RBAC 种子用"sys_user 表非空"做总开关，导致后加的按钮权限种不上；
    //   第二次就是把开关换成"有按钮就跳过"，于是再新增权限点又种不上。
    //
    // 正确做法是**逐条判断**：每一项自己检查"我是否已经存在"，不存在才插。
    // 这样每次启动都是安全的，且新加的东西能自动补上。
    // ============================================================
    @Transactional
    public void seedMenusAndPerms() {
        SysMenu systemMenu = findMenuByName("系统管理");
        if (systemMenu == null) {
            log.warn("找不到「系统管理」目录，跳过菜单/权限点初始化");
            return;
        }

        int created = 0;

        // ---------- 登录日志菜单 ----------
        if (sysMenuRepository.findByPerms("sys:loginlog:list").isEmpty()) {
            SysMenu loginLogMenu = new SysMenu();
            loginLogMenu.setMenuName("登录日志");
            loginLogMenu.setParentId(systemMenu.getId());
            loginLogMenu.setMenuType(SysMenu.TYPE_MENU);
            loginLogMenu.setPath("/system/login-logs");
            loginLogMenu.setPerms("sys:loginlog:list");
            loginLogMenu.setIcon("Document");
            loginLogMenu.setSortOrder(4);
            loginLogMenu.setVisible(1);
            sysMenuRepository.save(loginLogMenu);
            created++;
            log.info("新增菜单：登录日志");
        }

        // ---------- 部门管理菜单 ----------
        if (sysMenuRepository.findByPerms("sys:dept:list").isEmpty()) {
            SysMenu deptMenu = new SysMenu();
            deptMenu.setMenuName("部门管理");
            deptMenu.setParentId(systemMenu.getId());
            deptMenu.setMenuType(SysMenu.TYPE_MENU);
            deptMenu.setPath("/system/depts");
            deptMenu.setPerms("sys:dept:list");
            deptMenu.setIcon("OfficeBuilding");
            deptMenu.setSortOrder(5);
            deptMenu.setVisible(1);
            sysMenuRepository.save(deptMenu);
            created++;
            log.info("新增菜单：部门管理");
        }

        // ---------- 系统设置菜单 ----------
        if (sysMenuRepository.findByPerms("sys:config:list").isEmpty()) {
            SysMenu configMenu = new SysMenu();
            configMenu.setMenuName("系统设置");
            configMenu.setParentId(systemMenu.getId());
            configMenu.setMenuType(SysMenu.TYPE_MENU);
            configMenu.setPath("/system/configs");
            configMenu.setPerms("sys:config:list");
            configMenu.setIcon("Setting");
            configMenu.setSortOrder(7);
            configMenu.setVisible(1);
            sysMenuRepository.save(configMenu);
            created++;
            log.info("新增菜单：系统设置");
        }

        // ---------- 字典管理菜单 ----------
        if (sysMenuRepository.findByPerms("sys:dict:list").isEmpty()) {
            SysMenu dictMenu = new SysMenu();
            dictMenu.setMenuName("字典管理");
            dictMenu.setParentId(systemMenu.getId());
            dictMenu.setMenuType(SysMenu.TYPE_MENU);
            dictMenu.setPath("/system/dicts");
            dictMenu.setPerms("sys:dict:list");
            dictMenu.setIcon("Collection");
            dictMenu.setSortOrder(8);
            dictMenu.setVisible(1);
            sysMenuRepository.save(dictMenu);
            created++;
            log.info("新增菜单：字典管理");
        }

        // ---------- 按钮权限点 ----------
        SysMenu userMenu = findMenuByName("用户管理");
        SysMenu roleMenu = findMenuByName("角色管理");
        SysMenu menuMenu = findMenuByName("菜单管理");
        SysMenu deptMenu = findMenuByName("部门管理");
        SysMenu configMenu = findMenuByName("系统设置");
        SysMenu dictMenu = findMenuByName("字典管理");

        created += addButtons(userMenu, List.of(
                new String[]{"用户查询", "sys:user:list"},
                new String[]{"用户新增", "sys:user:add"},
                new String[]{"用户修改", "sys:user:edit"},
                new String[]{"用户删除", "sys:user:remove"},
                new String[]{"重置密码", "sys:user:reset"},
                new String[]{"分配角色", "sys:user:assign"},
                new String[]{"用户导出", "sys:user:export"}));

        created += addButtons(roleMenu, List.of(
                new String[]{"角色查询", "sys:role:list"},
                new String[]{"角色新增", "sys:role:add"},
                new String[]{"角色修改", "sys:role:edit"},
                new String[]{"角色删除", "sys:role:remove"},
                new String[]{"分配权限", "sys:role:assign"}));

        created += addButtons(menuMenu, List.of(
                new String[]{"菜单查询", "sys:menu:list"},
                new String[]{"菜单新增", "sys:menu:add"},
                new String[]{"菜单修改", "sys:menu:edit"},
                new String[]{"菜单删除", "sys:menu:remove"}));

        created += addButtons(deptMenu, List.of(
                new String[]{"部门查询", "sys:dept:list"},
                new String[]{"部门新增", "sys:dept:add"},
                new String[]{"部门修改", "sys:dept:edit"},
                new String[]{"部门删除", "sys:dept:remove"}));

        created += addButtons(configMenu, List.of(
                new String[]{"参数查询", "sys:config:list"},
                new String[]{"参数修改", "sys:config:edit"}));

        created += addButtons(dictMenu, List.of(
                new String[]{"字典查询", "sys:dict:list"},
                new String[]{"字典新增", "sys:dict:add"},
                new String[]{"字典修改", "sys:dict:edit"},
                new String[]{"字典删除", "sys:dict:remove"}));

        // ---------- 给超管角色补齐所有菜单/权限 ----------
        // 虽然 admin 角色在拦截器里会绕过权限检查，但补全它能让
        // 「角色管理 → 分配权限」页面上看到完整勾选状态，不至于一片空白
        sysRoleRepository.findByRoleKey("admin").ifPresent(adminRole -> {
            Set<SysMenu> menus = new HashSet<>(adminRole.getMenus());
            int before = menus.size();
            menus.addAll(sysMenuRepository.findAllByOrderBySortOrderAsc());
            if (menus.size() != before) {
                adminRole.setMenus(menus);
                sysRoleRepository.save(adminRole);
                log.info("已为超管角色补上 {} 个新增菜单/权限", menus.size() - before);
            }
        });

        if (created > 0) {
            log.info("菜单与权限点初始化完成，本次新增 {} 项", created);
        } else {
            log.info("菜单与权限点已是最新，无需变更");
        }
    }

    private SysMenu findMenuByName(String name) {
        for (SysMenu menu : sysMenuRepository.findAll()) {
            if (name.equals(menu.getMenuName())) {
                return menu;
            }
        }
        return null;
    }

    /** 给某个菜单挂一批按钮权限，返回**实际新建**的条数（已存在的会跳过，不计入） */
    private int addButtons(SysMenu parent, List<String[]> buttons) {
        return addButtons(parent, buttons, null);
    }

    /**
     * 同上，但把新建出来的节点也收集起来。
     *
     * @param createdOut 收集新建节点的列表，可为 null（不需要收集时）
     */
    private int addButtons(SysMenu parent, List<String[]> buttons, List<SysMenu> createdOut) {
        if (parent == null) {
            log.warn("找不到父菜单，跳过这批按钮权限：{}", buttons.isEmpty() ? "" : buttons.get(0)[0]);
            return 0;
        }

        int created = 0;
        int sort = 1;
        for (String[] button : buttons) {
            // 同一个权限标识只种一次。注意"用户查询"(sys:user:list) 这类会和
            // 父菜单自带的 perms 重复，这里会跳过它 —— 菜单本身已经代表了这个权限点。
            if (sysMenuRepository.existsByPerms(button[1])) {
                sort++;
                continue;
            }
            SysMenu node = new SysMenu();
            node.setMenuName(button[0]);
            node.setParentId(parent.getId());
            node.setMenuType(SysMenu.TYPE_BUTTON);
            node.setPerms(button[1]);
            node.setSortOrder(sort++);
            // 按钮不出现在侧边栏里
            node.setVisible(0);
            SysMenu saved = sysMenuRepository.save(node);
            if (createdOut != null) {
                createdOut.add(saved);
            }
            created++;
        }
        return created;
    }

    // ============================================================
    // 6. 演示维保计划 + 维保记录
    //
    // 和设备/部门一样是**逐条幂等**：每台设备自己检查"我有没有计划"，
    // 所以重复启动、以及在已有数据的库上启动都是安全的。
    // ============================================================
    @Transactional
    public void seedDemoMaintenance() {
        log.info("开始初始化演示维保计划...");

        // 到期日刻意做出三种情况：已逾期、快到期、还早。
        // 都设成"还早"的话，维保页面的告警和看板的提醒永远是空的，功能等于没验证过
        // 负责人填的是**用户名**（wangqiang/zhoutao），不是姓名 ——
        // 站内通知按用户名解析账号，填姓名的话发不出去。
        // 界面上会显示成昵称（前端拿用户选项做映射）
        createPlanIfAbsent("温度传感器 A", "季度保养", 90, -5, "wangqiang");
        createPlanIfAbsent("压力传感器", "季度保养", 90, 12, "wangqiang");
        createPlanIfAbsent("车间交换机", "半年保养", 180, 60, "zhoutao");
        createPlanIfAbsent("核心路由器", "半年保养", 180, 150, "zhoutao");

        // 给其中一台补一条历史保养记录，这样设备详情页的"维保记录"标签页
        // 一打开就有内容，不用先去点一次"执行维保"才知道长什么样
        createMaintenanceRecordIfAbsent("温度传感器 A");

        log.info("演示维保计划初始化完成（共 {} 个计划）", maintenancePlanRepository.count());
    }

    /**
     * 给某台设备建一个维保计划（如果还没有）。
     *
     * @param dueInDays 下次保养还有多少天（负数表示已经逾期）
     */
    private void createPlanIfAbsent(String deviceName, String planName, int cycleDays,
                                    int dueInDays, String maintainer) {
        Device device = findDeviceByName(deviceName);
        if (device == null) {
            // 设备不存在（比如用户把演示设备删了）就跳过，不能因为一条演示数据失败
            return;
        }
        if (maintenancePlanRepository.findByDeviceId(device.getId()).isPresent()) {
            return;
        }

        LocalDate next = LocalDate.now().plusDays(dueInDays);

        DeviceMaintenancePlan plan = new DeviceMaintenancePlan();
        plan.setDeviceId(device.getId());
        plan.setDeviceName(device.getDeviceName());
        plan.setPlanName(planName);
        plan.setCycleDays(cycleDays);
        plan.setMaintainer(maintainer);
        plan.setStatus(DeviceMaintenancePlan.STATUS_ENABLED);
        plan.setRemark("初始化的演示数据");
        plan.setNextMaintenanceDate(next);
        // 上次保养日由"下次到期日 - 周期"反推，这样两个日期是自洽的
        plan.setLastMaintenanceDate(next.minusDays(cycleDays));
        maintenancePlanRepository.save(plan);
    }

    private void createMaintenanceRecordIfAbsent(String deviceName) {
        Device device = findDeviceByName(deviceName);
        if (device == null) {
            return;
        }
        DeviceMaintenancePlan plan = maintenancePlanRepository.findByDeviceId(device.getId())
                .orElse(null);
        if (plan == null || maintenanceRecordRepository.countByPlanId(plan.getId()) > 0) {
            return;
        }

        DeviceMaintenanceRecord record = new DeviceMaintenanceRecord();
        record.setPlanId(plan.getId());
        record.setDeviceId(device.getId());
        record.setDeviceName(device.getDeviceName());
        record.setMaintenanceDate(plan.getLastMaintenanceDate());
        record.setMaintainer(plan.getMaintainer());
        record.setContent("检查接线与探头、清洁外壳、校准零点、记录当前读数");
        record.setResult(DeviceMaintenanceRecord.RESULT_NORMAL);
        record.setRemark("初始化的演示数据");
        maintenanceRecordRepository.save(record);
    }

    // ============================================================
    // 7. 演示配件耗材
    // ============================================================
    @Transactional
    public void seedDemoSpareParts() {
        log.info("开始初始化演示配件...");

        createPartIfAbsent("PJ-0001", "温度探头", "PT-100", "个", 20, 5, "35.00", "华东仪表", "A 区货架");
        createPartIfAbsent("PJ-0002", "压力变送器", "PRS-200", "个", 8, 3, "420.00", "华东仪表", "A 区货架");
        // 下面两个**故意低于预警阈值**，用来验证库存告警和页面标红
        createPartIfAbsent("PJ-0003", "密封圈", "OR-25", "个", 4, 10, "2.50", "通用五金", "B 区货架");
        createPartIfAbsent("PJ-0004", "网线接头", "RJ45", "个", 100, 20, "1.20", "通用五金", "B 区货架");
        createPartIfAbsent("PJ-0005", "继电器", "JQX-13F", "个", 2, 6, "18.00", "正泰电器", "C 区货架");

        log.info("演示配件初始化完成（共 {} 个配件）", sparePartRepository.count());
    }

    private void createPartIfAbsent(String code, String name, String model, String unit,
                                    int stock, int warnThreshold, String price,
                                    String supplier, String location) {
        if (sparePartRepository.existsByPartCode(code)) {
            return;
        }

        SparePart part = new SparePart();
        part.setPartCode(code);
        part.setPartName(name);
        part.setModel(model);
        part.setUnit(unit);
        // 先按 0 存，再通过一条入库流水把期初库存写进去。
        // 直接 setStockQuantity(stock) 的话，"当前库存 = 流水累加"这个恒等式
        // 从一开始就是破的，对不上账还查不出原因
        part.setStockQuantity(0);
        part.setWarnThreshold(warnThreshold);
        part.setUnitPrice(new BigDecimal(price));
        part.setSupplier(supplier);
        part.setLocation(location);
        part.setStatus(SparePart.STATUS_ENABLED);
        part.setRemark("初始化的演示数据");
        part = sparePartRepository.save(part);

        SparePartRecord record = new SparePartRecord();
        record.setPartId(part.getId());
        record.setPartCode(part.getPartCode());
        record.setPartName(part.getPartName());
        record.setRecordType(SparePartRecord.TYPE_IN);
        record.setQuantity(stock);
        record.setBeforeStock(0);
        record.setAfterStock(stock);
        record.setUnitPrice(part.getUnitPrice());
        record.setOperator("system");
        record.setRecordTime(LocalDateTime.now());
        record.setRemark("期初库存");
        sparePartRecordRepository.save(record);

        part.setStockQuantity(stock);
        sparePartRepository.save(part);
    }

    /** 按名称找设备。种子数据用，设备数量很少，直接遍历即可 */
    private Device findDeviceByName(String name) {
        for (Device device : deviceRepository.findAll()) {
            if (name.equals(device.getDeviceName())) {
                return device;
            }
        }
        return null;
    }

    // ============================================================
    // 8. 演示维修工单
    //
    // ⚠️ 这里**故意用粗粒度开关**（工单表非空就整体跳过），
    // 和上面那些"逐条判断"的种子数据不一样。
    //
    // 理由是这两种数据的性质不同：权限点、部门、配件都是"配置项"，
    // 每一项独立有意义，以后新增一项必须在老库上也能补上；
    // 而这几张演示工单是**一组相互关联的演示数据**，目的是让统计看板
    // 和状态流转一打开就有四种状态可看，单独补一条没有意义。
    // 而且用户的库里一旦有真实工单，就不该再往里塞演示单。
    // ============================================================
    @Transactional
    public void seedDemoRepairs() {
        if (deviceRepairRepository.count() > 0) {
            log.info("device_repair 表已有数据，跳过演示工单初始化");
            return;
        }
        log.info("开始初始化演示维修工单...");

        LocalDateTime now = LocalDateTime.now();

        // 四张单刻意覆盖四种状态，这样统计看板的柱状图四根柱子都有高度，
        // 状态流转的每个按钮也都能实际点到。
        // 故障类型也错开，验证字典驱动的筛选和展示
        createRepair("温度传感器 B", "读数跳变，偶尔报超限", "ELEC",
                DeviceRepair.STATUS_PENDING, "张强", null,
                now.minusHours(3), null, null, null, null, null);

        createRepair("车间交换机", "端口 12 频繁闪断", "COMM",
                DeviceRepair.STATUS_REPAIRING, "李工", "wangqiang",
                now.minusDays(1), now.minusHours(20), null, null, null, null);

        createRepair("核心路由器", "电源模块异响，重启后恢复", "ELEC",
                DeviceRepair.STATUS_FINISHED, "赵敏", "zhoutao",
                now.minusDays(5), now.minusDays(5).plusHours(2),
                now.minusDays(5).plusHours(6),
                "拆机检查电源模块，发现风扇积灰卡滞。清灰并更换风扇，"
                        + "带载测试 2 小时温度正常，异响消失。",
                new BigDecimal("180.00"), null);

        // 这一张故意**不填故障类型**，用来验证老工单/未分类的情况不会报错
        createRepair("激光打印机", "卡纸频繁（误报，实为纸张受潮）", null,
                DeviceRepair.STATUS_CLOSED, "刘芳", null,
                now.minusDays(8), null, null, null, null,
                "现场检查设备正常，是纸张受潮导致，更换纸张后恢复，非设备故障");

        // 演示工单要连带把设备状态调整到位，否则页面会出现
        // "工单维修中但设备显示在线"这种自相矛盾的情况
        syncDeviceStatus("温度传感器 B", Device.STATUS_REPAIRING);
        syncDeviceStatus("车间交换机", Device.STATUS_REPAIRING);

        log.info("演示维修工单初始化完成（共 {} 张，覆盖四种状态）",
                deviceRepairRepository.count());
    }

    /**
     * 建一张工单 + 对应的流转日志。
     *
     * <p>注意这里**直接走 Repository**，不走 DeviceRepairService：
     * 走 Service 会发布 RepairCreatedEvent 触发 AI 分析，
     * 启动时就会去连 Ollama，连不上虽然不影响启动但会在日志里刷一片失败。
     */
    private void createRepair(String deviceName, String faultDesc, String faultType, String status,
                              String reporter, String repairer,
                              LocalDateTime reportTime, LocalDateTime acceptTime,
                              LocalDateTime finishTime, String repairResult,
                              BigDecimal cost, String closeReason) {
        Device device = findDeviceByName(deviceName);
        if (device == null) {
            // 设备不存在（用户删了演示设备）就跳过，不能因为一条演示数据失败
            return;
        }

        DeviceRepair repair = new DeviceRepair();
        repair.setDeviceId(device.getId());
        repair.setDeviceName(device.getDeviceName());
        repair.setFaultDesc(faultDesc);
        // 故障类型存的是**字典项的值**（MECH/ELEC/…），不是展示文案
        repair.setFaultType(faultType);
        repair.setRepairStatus(status);
        repair.setReporter(reporter);
        repair.setRepairer(repairer);
        repair.setReportTime(reportTime);
        repair.setAcceptTime(acceptTime);
        repair.setFinishTime(finishTime);
        repair.setRepairResult(repairResult);
        repair.setCost(cost);
        repair.setCloseReason(closeReason);
        if (DeviceRepair.STATUS_CLOSED.equals(status)) {
            repair.setCloseTime(reportTime.plusHours(4));
        }
        if (repairer != null) {
            repair.setAssignTime(reportTime.plusMinutes(30));
        }
        deviceRepairRepository.save(repair);

        // 补上流转日志，这样详情抽屉里的时间线不是空的
        writeRepairLog(repair.getId(), DeviceRepairLog.TYPE_CREATED,
                "工单已创建，故障描述：" + faultDesc, reporter, reportTime);
        if (acceptTime != null) {
            writeRepairLog(repair.getId(), DeviceRepairLog.TYPE_STATUS,
                    "工单已受理，开始维修（维修人：" + repairer + "）", repairer, acceptTime);
        }
        if (finishTime != null) {
            writeRepairLog(repair.getId(), DeviceRepairLog.TYPE_STATUS,
                    "工单已完成，维修人：" + repairer + "；维修结果：" + repairResult,
                    repairer, finishTime);
        }
        if (closeReason != null) {
            writeRepairLog(repair.getId(), DeviceRepairLog.TYPE_STATUS,
                    "工单已关闭（受理前作废），原因：" + closeReason,
                    reporter, repair.getCloseTime());
        }
    }

    private void writeRepairLog(Long repairId, String logType, String content,
                                String operator, LocalDateTime logTime) {
        DeviceRepairLog entry = new DeviceRepairLog();
        entry.setRepairId(repairId);
        entry.setLogType(logType);
        entry.setContent(content);
        entry.setOperator(operator);
        entry.setLogTime(logTime);
        deviceRepairLogRepository.save(entry);
    }

    /** 按名称把设备状态调到指定值（演示工单和真实流转保持一致） */
    private void syncDeviceStatus(String deviceName, String status) {
        Device device = findDeviceByName(deviceName);
        if (device == null) {
            return;
        }
        device.setStatus(status);
        // 报修会把借用信息清掉，演示数据也照做，避免出现"维修中 + 有借用人"
        device.setBorrower(null);
        device.setBorrowTime(null);
        deviceRepository.save(device);
    }

    // ============================================================
    // 9. 业务模块的菜单与权限点
    //
    // 和上面的 seedMenusAndPerms 一样是**逐条幂等**（每项自己检查是否已存在）。
    //
    // 分两个方法而不是合并：那个方法负责「系统管理」目录下的东西，
    // 这个方法负责业务模块。混在一起会让"新增一个权限点该往哪写"变得不明确。
    // ============================================================
    @Transactional
    public void seedBusinessMenusAndPerms() {
        SysMenu systemMenu = findMenuByName("系统管理");

        // 本次**新建出来**的节点（菜单 + 按钮）。只有这些才参与
        // "给 operator 授默认权限"，见 grantOperatorDefaults 的说明。
        // ⚠️ 必须先声明：下面的 ensureMenu 就要往里面收集
        List<SysMenu> createdNodes = new ArrayList<>();

        // ---------- 父菜单（权限点挂在它们下面，角色授权树才显示得出来） ----------
        //
        // ⚠️ 新建出来的**菜单节点本身**也要收进 createdNodes：
        // 菜单上挂着它自己的权限点（比如「设备台账」菜单的 dev:ledger:list），
        // 而 addButtons 会因为"这个权限标识已存在"而跳过同名的按钮节点 ——
        // 于是这个权限点根本不会出现在 createdNodes 里。
        // 漏掉它的后果是：给 operator 授权时正好漏掉这几个**模块的入口权限**，
        // 表现成 operator 能查设备却打不开台账/维保/配件/工单，而且报的是 403。
        SysMenu deviceMenu = ensureMenu("设备管理", SysMenu.ROOT_PARENT_ID, "M",
                "/devices", "dev:device:list", "Monitor", 1, createdNodes);
        SysMenu ledgerMenu = ensureMenu("设备台账", SysMenu.ROOT_PARENT_ID, "M",
                "/devices/ledger", "dev:ledger:list", "DataLine", 2, createdNodes);
        SysMenu maintMenu = ensureMenu("维保管理", SysMenu.ROOT_PARENT_ID, "M",
                "/maintenance", "dev:maint:list", "Refresh", 3, createdNodes);
        SysMenu partMenu = ensureMenu("配件耗材", SysMenu.ROOT_PARENT_ID, "M",
                "/spare-parts", "dev:part:list", "Box", 4, createdNodes);
        SysMenu repairMenu = ensureMenu("维修工单", SysMenu.ROOT_PARENT_ID, "M",
                "/device-repairs", "dev:repair:list", "Tools", 5, createdNodes);
        SysMenu operLogMenu = systemMenu == null ? null
                : ensureMenu("操作日志", systemMenu.getId(), "F",
                        "/system/oper-logs", "sys:operlog:list", "Document", 6, createdNodes);
        // 资产审计中心。排在最后（系统管理下现有的最大序号是「字典管理」的 8）
        SysMenu auditMenu = systemMenu == null ? null
                : ensureMenu("资产审计中心", systemMenu.getId(), "F",
                        "/system/audit-logs", "sys:audit:list", "Audit", 9, createdNodes);
        // 设备知识库（RAG 的源文件管理）。顶级的，因为它是业务功能不是系统设置
        SysMenu knowledgeMenu = ensureMenu("设备知识库", SysMenu.ROOT_PARENT_ID, "M",
                "/knowledge", "sys:knowledge:list", "Collection", 6, createdNodes);

        // ---------- 设备 ----------
        addButtons(deviceMenu, List.of(
                new String[]{"设备查询", "dev:device:list"},
                new String[]{"设备新增", "dev:device:add"},
                new String[]{"设备修改", "dev:device:edit"},
                new String[]{"设备删除", "dev:device:remove"},
                new String[]{"设备导出", "dev:device:export"},
                new String[]{"设备导入", "dev:device:import"},
                new String[]{"设备借用", "dev:device:borrow"},
                new String[]{"设备归还", "dev:device:return"},
                new String[]{"设备报修", "dev:device:repair"},
                new String[]{"设备调拨", "dev:device:transfer"},
                new String[]{"设备报废", "dev:device:scrap"},
                new String[]{"附件上传", "dev:attachment:upload"},
                new String[]{"附件删除", "dev:attachment:remove"}),
                createdNodes);

        // ---------- 设备台账 ----------
        addButtons(ledgerMenu, List.of(
                new String[]{"台账查看", "dev:ledger:list"},
                new String[]{"台账导出", "dev:ledger:export"}),
                createdNodes);

        // ---------- 维修工单 ----------
        addButtons(repairMenu, List.of(
                new String[]{"工单查询", "dev:repair:list"},
                new String[]{"工单受理", "dev:repair:accept"},
                new String[]{"指派维修人", "dev:repair:assign"},
                new String[]{"工单完工", "dev:repair:finish"},
                new String[]{"工单关闭", "dev:repair:close"},
                new String[]{"工单删除", "dev:repair:remove"},
                new String[]{"工单导出", "dev:repair:export"},
                new String[]{"维修记录", "dev:repair:log"},
                new String[]{"AI 重新分析", "dev:repair:analyze"}),
                createdNodes);

        // ---------- 维保 ----------
        addButtons(maintMenu, List.of(
                new String[]{"维保查询", "dev:maint:list"},
                new String[]{"维保计划新增", "dev:maint:add"},
                new String[]{"维保计划修改", "dev:maint:edit"},
                new String[]{"维保计划删除", "dev:maint:remove"},
                new String[]{"执行维保", "dev:maint:execute"}),
                createdNodes);

        // ---------- 配件 ----------
        addButtons(partMenu, List.of(
                new String[]{"配件查询", "dev:part:list"},
                new String[]{"配件新增", "dev:part:add"},
                new String[]{"配件修改", "dev:part:edit"},
                new String[]{"配件删除", "dev:part:remove"},
                new String[]{"出入库", "dev:part:stock"}),
                createdNodes);

        // ---------- 操作日志 ----------
        if (operLogMenu != null) {
            // ⚠️ 单个数组必须写 List.<String[]>of(...)。
            // 写成 List.of(new String[]{...}) 时，Java 会把那个数组**当成 varargs 展开**，
            // 推断出 List<String> 而不是 List<String[]>，编译报
            // "inference variable E has incompatible bounds" —— 报错信息完全看不出真正原因。
            // 多元素时不会有这个问题（形如 List.of(a, b) 只能是 List<String[]>）。
            addButtons(operLogMenu, List.<String[]>of(
                    new String[]{"操作日志查询", "sys:operlog:list"}),
                    createdNodes);
        }

        // ---------- 资产审计 ----------
        if (auditMenu != null) {
            // 这里**只加导出**，「审计查询」（sys:audit:list）由 auditMenu 这个菜单节点
            // 自己承载 —— 菜单上也挂着 perms，已经被 ensureMenu 收进了 createdNodes。
            // 再写一遍的话 addButtons 会因为"权限标识已存在"跳过它，
            // 等于留一段永远不会执行的死代码
            addButtons(auditMenu, List.<String[]>of(
                    new String[]{"审计导出", "sys:audit:export"}),
                    createdNodes);
        }

        // ---------- 设备知识库 ----------
        if (knowledgeMenu != null) {
            // 「知识库查询」由菜单节点自己承载，理由同审计
            addButtons(knowledgeMenu, List.of(
                    new String[]{"知识库上传", "sys:knowledge:add"},
                    new String[]{"知识库删除", "sys:knowledge:remove"}),
                    createdNodes);
        }

        // ---------- 给超管补齐（让角色授权树上的勾选状态完整） ----------
        grantAllToAdmin();

        // ---------- 给普通操作员授一套默认权限 ----------
        grantOperatorDefaults(createdNodes);

        if (!createdNodes.isEmpty()) {
            log.info("业务模块权限点初始化完成，本次新增 {} 项", createdNodes.size());
        } else {
            log.info("业务模块权限点已是最新，无需变更");
        }
    }

    /**
     * 普通操作员的默认权限：**业务模块里除了「删除」以外的全部**。
     *
     * <p>为什么是这个口径：用户的需求里举的例子是「普通操作员看不到删除按钮，
     * 只有超级管理员可以删除」。所以默认只摘掉删除类权限 ——
     * 一上来就把新增/编辑/报废/调拨全收走会让人措手不及，
     * 而"再收紧一点"在角色管理页上点几下就能做到，反过来却不好发现。
     *
     * <p>系统管理那一整块（用户/角色/菜单/部门/操作日志/登录日志）不在此列，
     * 那些接口上标的是 @RequireRole("admin")，跟权限点无关。
     */
    private static final Set<String> OPERATOR_EXCLUDED_PERMS = Set.of(
            "dev:device:remove",
            "dev:attachment:remove",
            "dev:repair:remove",
            "dev:maint:remove",
            "dev:part:remove",
            // 批量导入虽然不删数据，但**一次能写进几千条**，影响面和删除是一个量级，
            // 所以同删除类一起默认不给操作员
            "dev:device:import",
            // 操作日志属于审计资料，只给管理员。它虽然也走权限点，
            // 但除了 admin 之外的任何角色都不该拿到
            "sys:operlog:list",
            // 资产审计同理。它是「谁在什么时候改了哪台设备的哪个字段」，
            // 属于合规追溯资料而不是日常业务数据 —— 和操作日志保持同一个口径。
            // 另外设备详情页的「变更审计」页签也归它管，前端会用 hasPerm 把页签藏掉，
            // 不会出现"点进去 403"的情况。
            // 如果以后想让操作员也能看自己部门的设备变更史，
            // 把 sys:audit:list 从这里去掉即可（导出仍然只给管理员）
            "sys:audit:list",
            "sys:audit:export",
            // 知识库是**配置类**功能：往里传的是全局资料，所有检索都基于它。
            // 和系统设置、字典管理是一个性质，默认不给操作员。
            //
            // 以后「智能故障诊断」要让操作员能检索，正确做法是另开一个
            // 只要求登录的检索接口，而不是把 list 权限放开 ——
            // 那样会连文档的上传、删除、块内容一起放开
            "sys:knowledge:list",
            "sys:knowledge:add",
            "sys:knowledge:remove");

    /**
     * 给操作员角色补上**本次新建**的默认权限。
     *
     * <p>⚠️ 这里只处理"本次新建出来的权限点"，不是每次启动都把默认权限塞回去。
     *
     * <p>原因：管理员可能有意把某个权限从 operator 身上摘掉。
     * 如果每次启动都无条件补一遍，那个操作会在下次重启后被悄悄撤销，
     * 而且**不会报错**，管理员只会觉得"我明明取消过，怎么又有了" ——
     * 这类"配置被系统自己改回去"的问题最难排查。
     *
     * <p>代价：如果新增的权限点第一次没种上（比如那次启动失败了），
     * 后续启动不会再给 operator 补。这种情况去角色管理页手动勾一下即可，
     * 比"撤销无效"要轻得多。
     */
    private void grantOperatorDefaults(List<SysMenu> createdNodes) {
        if (createdNodes.isEmpty()) {
            return;
        }
        List<SysMenu> grantable = createdNodes.stream()
                .filter(node -> node.getPerms() != null)
                .filter(node -> !OPERATOR_EXCLUDED_PERMS.contains(node.getPerms()))
                .toList();
        if (grantable.isEmpty()) {
            return;
        }

        sysRoleRepository.findByRoleKey("operator").ifPresent(operatorRole -> {
            Set<SysMenu> menus = new HashSet<>(operatorRole.getMenus());
            int before = menus.size();
            menus.addAll(grantable);
            if (menus.size() != before) {
                operatorRole.setMenus(menus);
                sysRoleRepository.save(operatorRole);
                log.info("已给「普通操作员」默认授上 {} 个新权限点（删除类除外，可在角色管理页调整）",
                        menus.size() - before);
            }
        });
    }

    /** 给超管角色补上全部菜单/权限，让角色授权树上的勾选状态完整 */
    private void grantAllToAdmin() {
        sysRoleRepository.findByRoleKey("admin").ifPresent(adminRole -> {
            Set<SysMenu> menus = new HashSet<>(adminRole.getMenus());
            int before = menus.size();
            menus.addAll(sysMenuRepository.findAllByOrderBySortOrderAsc());
            if (menus.size() != before) {
                adminRole.setMenus(menus);
                sysRoleRepository.save(adminRole);
                log.info("已为超管角色补上 {} 个新增菜单/权限", menus.size() - before);
            }
        });
    }

    /**
     * 确保某个菜单存在（按名称查），返回它。
     *
     * <p>已存在就原样返回**不改动** —— 管理员改过菜单名称或排序的话，
     * 不该被种子数据改回去。
     *
     * @param createdOut 新建时把节点收集进去（用于给 operator 授默认权限），可为 null
     */
    private SysMenu ensureMenu(String name, Long parentId, String menuType,
                               String path, String perms, String icon, int sortOrder,
                               List<SysMenu> createdOut) {
        SysMenu existing = findMenuByName(name);
        if (existing != null) {
            return existing;
        }
        SysMenu menu = createMenu(name, parentId, menuType, path, null, perms, icon, sortOrder);
        SysMenu saved = sysMenuRepository.save(menu);
        if (createdOut != null) {
            createdOut.add(saved);
        }
        log.info("新增菜单：{}", name);
        return saved;
    }

    // ============================================================
    // 10. 初始默认密码的标记
    //
    // 老库里的 admin 是早就存在的行，加 must_change_password 列时拿不到标记
    // （ddl-auto 只加列不回填），所以要在启动时补一次。
    // ============================================================
    @Transactional
    public void repairDefaultPasswords() {
        int flagged = 0;

        for (Map.Entry<String, String> entry : PasswordPolicy.KNOWN_DEFAULT_PASSWORDS.entrySet()) {
            SysUser user = sysUserRepository.findByUsername(entry.getKey()).orElse(null);
            if (user == null) {
                continue;
            }
            // 已经标记过就不重复处理
            if (user.isMustChangePassword()) {
                continue;
            }
            // 密码已经改过（和文档里的初始值对不上了）就什么都不做。
            // 注意这里只能做哈希比对 —— 库里存的不是明文，没法直接看
            if (!passwordEncoder.matches(entry.getValue(), user.getPassword())) {
                continue;
            }
            user.setMustChangePassword(Boolean.TRUE);
            sysUserRepository.save(user);
            flagged++;
        }

        if (flagged > 0) {
            log.warn("检测到 {} 个账号仍在使用初始默认密码，已标记为「必须修改密码」。"
                    + "这些账号下次登录会被要求改密。", flagged);
        }
    }

    // ============================================================
    // 11. 系统参数默认值
    //
    // 这些值和 application.yml 里的默认值是一致的 —— 库里存的是"覆盖值"，
    // 删掉某一行就会回退到 yml 的默认值，系统不会因此跑不起来。
    //
    // 全部标成 builtIn=true（不允许删除，只能改值）：它们对应代码里的
    // getInt(key, 默认值) 调用点，删掉之后代码不报错、只是悄悄用回默认值，
    // 管理员会以为"我明明配过、怎么不生效"。
    // ============================================================
    @Transactional
    public void seedSystemConfigs() {
        int created = 0;

        created += ensureConfig(ConfigKeys.SYSTEM_NAME, ConfigKeys.SYSTEM_NAME_DEFAULT,
                "系统名称", "系统信息", SysConfig.TYPE_STRING, 1,
                "显示在登录页、侧边栏标题和浏览器标签页上");

        created += ensureConfig(ConfigKeys.COMPANY_NAME, ConfigKeys.COMPANY_NAME_DEFAULT,
                "企业名称", "系统信息", SysConfig.TYPE_STRING, 2,
                "显示在登录页上");

        created += ensureConfig(ConfigKeys.SYSTEM_BASE_URL,
                ConfigKeys.SYSTEM_BASE_URL_DEFAULT,
                "对外访问地址", "系统信息", SysConfig.TYPE_STRING, 3,
                "填这台服务器在局域网/公网上的地址，例如 http://192.168.1.20:8080。"
                        + "设备资产标签上的二维码用的就是它 —— 二维码是给别的设备（手机）扫的，"
                        + "所以必须填别的设备能访问到的地址。留空表示用当前页面的地址，"
                        + "那样在开发机上扫出来是打不开的");

        created += ensureConfig(ConfigKeys.PASSWORD_VALID_DAYS,
                String.valueOf(ConfigKeys.PASSWORD_VALID_DAYS_DEFAULT),
                "密码有效期（天）", "安全策略", SysConfig.TYPE_NUMBER, 1,
                "密码到期后登录会被要求先改密码。填 0 表示关闭过期策略");

        created += ensureConfig(ConfigKeys.MAINTENANCE_WARN_DAYS,
                String.valueOf(ConfigKeys.MAINTENANCE_WARN_DAYS_DEFAULT),
                "维保到期预警（天）", "提醒阈值", SysConfig.TYPE_NUMBER, 1,
                "维保页面告警和首页看板都按这个天数提前提醒。和 yml 里的 app.maintenance.warn-days 同义");

        created += ensureConfig(ConfigKeys.WARRANTY_WARN_DAYS,
                String.valueOf(ConfigKeys.WARRANTY_WARN_DAYS_DEFAULT),
                "保修到期预警（天）", "提醒阈值", SysConfig.TYPE_NUMBER, 2,
                "首页看板的「保修即将到期」清单按这个天数提前提醒");

        created += ensureConfig(ConfigKeys.UPLOAD_MAX_SIZE_MB,
                String.valueOf(ConfigKeys.UPLOAD_MAX_SIZE_MB_DEFAULT),
                "附件大小上限（MB）", "附件", SysConfig.TYPE_NUMBER, 1,
                "单个附件的大小上限。注意它必须小于 yml 里 spring.servlet.multipart.max-file-size，"
                        + "否则提示会变成笼统的「文件过大」");

        created += ensureConfig(ConfigKeys.MAINTENANCE_NOTIFY_ENABLED,
                String.valueOf(ConfigKeys.MAINTENANCE_NOTIFY_ENABLED_DEFAULT),
                "维保到期自动发消息", "提醒阈值", SysConfig.TYPE_BOOLEAN, 3,
                "关掉之后维保到期只在页面和看板告警，不再给负责人发站内消息");

        created += ensureConfig(ConfigKeys.STOCK_NOTIFY_ENABLED,
                String.valueOf(ConfigKeys.STOCK_NOTIFY_ENABLED_DEFAULT),
                "库存告急自动发消息", "提醒阈值", SysConfig.TYPE_BOOLEAN, 4,
                "配件库存降到预警阈值以下时，给负责库存的人发站内消息。"
                        + "一件配件每天只发一条，补货后自动停止");

        created += ensureConfig(ConfigKeys.HEALTH_NOTIFY_ENABLED,
                String.valueOf(ConfigKeys.HEALTH_NOTIFY_ENABLED_DEFAULT),
                "健康预警自动发消息", "提醒阈值", SysConfig.TYPE_BOOLEAN, 5,
                "每天把健康分偏低的设备汇总成一条消息，发给拥有「设备修改」权限的用户和管理员。"
                        + "阈值和首页看板的健康预警一致（低于 90 分）");

        created += ensureConfig(ConfigKeys.DASHBOARD_DIGEST_ENABLED,
                String.valueOf(ConfigKeys.DASHBOARD_DIGEST_ENABLED_DEFAULT),
                "首页 AI 摘要", "首页看板", SysConfig.TYPE_BOOLEAN, 1,
                "用模型把看板上的数字组织成一段人话，每天早上自动生成一次。"
                        + "关掉之后首页其余部分完全不受影响，只是不再显示这张卡片");

        created += seedAiConfigs();

        // 种完立刻清缓存，否则这次启动读到的还是"没有参数"的空快照
        sysConfigService.evictCache();

        if (created > 0) {
            log.info("系统参数初始化完成，本次新增 {} 项", created);
        }
    }

    /**
     * AI 模型的配置项。
     *
     * <p><b>⚠️ 七项的种子值**全部是空串**，不是具体的默认值。</b>
     * 空 = "不覆盖，跟随配置文件 / 环境变量"。
     *
     * <p>一开始只有 base-url 是空的，后来发现 provider 和 model 种成字面值同样有坑：
     * Docker 里注入的 {@code AI_EMBED_MODEL=bge-m3} 会被库里的
     * {@code nomic-embed-text} 盖掉，而管理员在界面上完全看不出
     * "为什么改了环境变量不生效" —— 我自己就在测试里踩了这一次。
     *
     * <p>统一成一条规则之后，行为是可预测的：**库里为空就走配置文件，填了才覆盖**。
     * 界面上把**实际生效的值**放在占位提示里（"留空则用：xxx"），
     * 所以看到空框也不会误以为没配。
     */
    private int seedAiConfigs() {
        int created = 0;
        String group = "AI 模型";

        created += ensureConfig(ConfigKeys.AI_CHAT_PROVIDER,
                ConfigKeys.AI_CHAT_PROVIDER_DEFAULT,
                "对话提供方", group, SysConfig.TYPE_STRING, 1,
                "ollama = 本机/自建的 Ollama（用它自己的原生接口）；"
                        + "openai = 任意 OpenAI 兼容服务（DeepSeek、通义千问、Kimi、智谱、"
                        + "硅基流动、vLLM、LM Studio 等）。留空表示用配置文件里的值");

        created += ensureConfig(ConfigKeys.AI_CHAT_BASE_URL,
                ConfigKeys.AI_CHAT_BASE_URL_DEFAULT,
                "对话服务地址", group, SysConfig.TYPE_STRING, 2,
                "留空表示用配置文件/环境变量里的值。本机 Ollama 填 http://localhost:11434；"
                        + "OpenAI 兼容服务要**填到版本段为止**："
                        + "DeepSeek https://api.deepseek.com/v1 ｜ "
                        + "通义千问 https://dashscope.aliyuncs.com/compatible-mode/v1 ｜ "
                        + "Kimi https://api.moonshot.cn/v1 ｜ "
                        + "硅基流动 https://api.siliconflow.cn/v1 ｜ "
                        + "本机 LM Studio http://localhost:1234/v1");

        created += ensureConfig(ConfigKeys.AI_CHAT_MODEL,
                ConfigKeys.AI_CHAT_MODEL_DEFAULT,
                "对话模型", group, SysConfig.TYPE_STRING, 3,
                "AI 助手、故障分析、故障诊断都用这个模型。"
                        + "本机 Ollama 例如 qwen3.5:4b；DeepSeek 用 deepseek-chat");

        created += ensureConfig(ConfigKeys.AI_CHAT_TEMPERATURE,
                ConfigKeys.AI_CHAT_TEMPERATURE_DEFAULT,
                "对话随机度", group, SysConfig.TYPE_NUMBER, 4,
                "0 最确定、1 最随机。小模型建议 0.5~0.8，太高会开始胡言乱语");

        created += ensureConfig(ConfigKeys.AI_CHAT_THINKING,
                ConfigKeys.AI_CHAT_THINKING_DEFAULT,
                "先思考再回答", group, SysConfig.TYPE_BOOLEAN, 5,
                "让模型把推理过程先走一遍再给答案。质量可能好一点，但**慢好几倍**。"
                        + "只对 Ollama 原生接口生效；换成 OpenAI 兼容提供方后这个开关会失效。"
                        + "留空表示用配置文件里的值");

        created += ensureConfig(ConfigKeys.AI_EMBEDDING_PROVIDER,
                ConfigKeys.AI_EMBEDDING_PROVIDER_DEFAULT,
                "嵌入提供方", group, SysConfig.TYPE_STRING, 6,
                "可以和对话用不同的提供方 —— 嵌入量大、按量计费不划算，"
                        + "本机跑往往更合适");

        created += ensureConfig(ConfigKeys.AI_EMBEDDING_BASE_URL,
                ConfigKeys.AI_EMBEDDING_BASE_URL_DEFAULT,
                "嵌入服务地址", group, SysConfig.TYPE_STRING, 7,
                "和对话的格式一样，要填到版本段为止。留空表示用配置文件/环境变量里的值。"
                        + "本机 Ollama 用 http://localhost:11434（走它的原生嵌入接口，不带 /v1）");

        created += ensureConfig(ConfigKeys.AI_EMBEDDING_MODEL,
                ConfigKeys.AI_EMBEDDING_MODEL_DEFAULT,
                "嵌入模型", group, SysConfig.TYPE_STRING, 8,
                "⚠️ 换模型会让已入库的向量全部失效，需要对每份文档点一次「重新处理」。"
                        + "本机 Ollama 用 nomic-embed-text（要单独 ollama pull，约 270MB）；"
                        + "硅基流动可用 BAAI/bge-m3");

        return created;
    }

    private int ensureConfig(String key, String value, String name, String group,
                             String valueType, int sortOrder, String remark) {
        if (sysConfigRepository.existsByConfigKey(key)) {
            return 0;
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setConfigName(name);
        config.setConfigGroup(group);
        config.setValueType(valueType);
        config.setSortOrder(sortOrder);
        config.setRemark(remark);
        config.setBuiltIn(Boolean.TRUE);
        sysConfigRepository.save(config);
        return 1;
    }

    // ============================================================
    // 12. 字典默认数据
    //
    // ⚠️ 这里**只放"纯分类、不参与逻辑判断"的枚举**。
    // 设备状态（正常/维修/报废/停用）、工单状态（待受理/维修中/…）
    // 这些状态机的值继续用代码常量 —— 它们的流转规则写在 Service 里，
    // 变成可随意编辑的字典项之后，有人改一个值就会让所有判断**静默失效**。
    // ============================================================
    @Transactional
    public void seedDictionaries() {
        SysDictType faultType = ensureDictType(DictTypes.FAULT_TYPE, "故障类型",
                "报修时选择的故障分类，工单列表可按它筛选");
        if (faultType == null) {
            return;   // 上面打过了警告
        }

        int created = 0;
        created += ensureDictItem(DictTypes.FAULT_TYPE, "MECH", "机械故障", 1);
        created += ensureDictItem(DictTypes.FAULT_TYPE, "ELEC", "电气故障", 2);
        created += ensureDictItem(DictTypes.FAULT_TYPE, "COMM", "通信故障", 3);
        created += ensureDictItem(DictTypes.FAULT_TYPE, "SOFT", "软件故障", 4);
        created += ensureDictItem(DictTypes.FAULT_TYPE, "OTHER", "其他", 9);

        // 和参数一样：种完清缓存，否则这次启动读到的还是空字典
        sysDictService.evictCache();

        if (created > 0) {
            log.info("字典初始化完成，本次新增 {} 个字典项", created);
        }
    }

    private SysDictType ensureDictType(String dictType, String dictName, String remark) {
        return sysDictTypeRepository.findByDictType(dictType).orElseGet(() -> {
            SysDictType type = new SysDictType();
            type.setDictType(dictType);
            type.setDictName(dictName);
            type.setRemark(remark);
            type.setStatus(SysDictType.STATUS_NORMAL);
            log.info("新增字典类型：{}", dictName);
            return sysDictTypeRepository.save(type);
        });
    }

    private int ensureDictItem(String dictType, String value, String label, int sortOrder) {
        if (sysDictItemRepository.existsByDictTypeAndItemValue(dictType, value)) {
            return 0;
        }
        SysDictItem item = new SysDictItem();
        item.setDictType(dictType);
        item.setItemValue(value);
        item.setItemLabel(label);
        item.setSortOrder(sortOrder);
        item.setStatus(SysDictItem.STATUS_NORMAL);
        sysDictItemRepository.save(item);
        return 1;
    }

    // ============================================================
    // 13. 演示用户（维修工 / 设备管理员）
    //
    // ⚠️ 这批账号是给**站内通知**当收件人用的，不是可有可无的装饰。
    //
    // 工单的「维修人」和维保计划的「负责人」存的是**用户名**，
    // 通知按用户名解析账号。如果系统里只有 admin/operator，
    // 演示数据里的"王强""周涛"就找不到账号，指派通知会直接发不出去 ——
    // 功能看起来"做了"，实际是空的。
    //
    // 所以这几个账号必须存在，通知链路才走得通。
    // ============================================================
    @Transactional
    public void seedDemoUsers() {
        SysRole operatorRole = sysRoleRepository.findByRoleKey("operator").orElse(null);
        if (operatorRole == null) {
            log.warn("找不到 operator 角色，跳过演示用户初始化");
            return;
        }

        int created = 0;
        created += ensureUser("wangqiang", "王强", operatorRole);
        created += ensureUser("zhoutao", "周涛", operatorRole);
        created += ensureUser("ligong", "李工", operatorRole);
        created += ensureUser("zhaomin", "赵敏", operatorRole);

        if (created > 0) {
            log.warn("新增了 {} 个演示账号（wangqiang/zhoutao/ligong/zhaomin，"
                    + "统一密码 {}）。它们是演示数据，上线前请删除或改密。",
                    created, DEMO_USER_PASSWORD);
        }
    }

    /** 演示账号的统一密码。符合后端的密码强度策略（8-64 位、四类字符占三类） */
    private static final String DEMO_USER_PASSWORD = "User@123456";

    private int ensureUser(String username, String nickname, SysRole role) {
        if (sysUserRepository.existsByUsername(username)) {
            return 0;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEMO_USER_PASSWORD));
        user.setNickname(nickname);
        user.setStatus("正常");
        user.setPwdUpdateTime(LocalDateTime.now());
        // ★ 演示账号**不强制改密**：否则每换一个账号演示都要先走一遍改密流程。
        // 代价是它们带着公开的密码存在，所以上面的日志里明确提示了上线前要处理
        user.setMustChangePassword(Boolean.FALSE);
        Set<SysRole> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        sysUserRepository.save(user);
        return 1;
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
                            String assetCode, String serialNumber, String status, String location,
                            Long deptId, String model, String manufacturer, String lifecycleStatus) {
        Device device = new Device();
        device.setDeviceName(name);
        device.setDeviceType(deviceType);
        device.setCategoryId(categoryId);
        device.setAssetCode(assetCode);
        device.setSerialNumber(serialNumber);
        device.setStatus(status);
        device.setLocation(location);
        device.setDeptId(deptId);
        device.setModel(model);
        device.setManufacturer(manufacturer);
        device.setLifecycleStatus(lifecycleStatus);
        device.setPurchaseDate(LocalDate.now().minusMonths(10));
        device.setWarrantyDate(LocalDate.now().plusMonths(14));
        device.setDescription("初始化的演示数据");
        deviceRepository.save(device);
    }
}
