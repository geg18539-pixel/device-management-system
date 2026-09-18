package com.yan.backend.config;

import com.yan.backend.entity.SysMenu;
import com.yan.backend.entity.SysRole;
import com.yan.backend.entity.SysUser;
import com.yan.backend.repository.SysMenuRepository;
import com.yan.backend.repository.SysRoleRepository;
import com.yan.backend.repository.SysUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 开发环境初始数据。
 *
 * <p>用 CommandLineRunner 在应用启动完成后自动插入管理员账号、角色和菜单，
 * 省得每次换数据库都要手工执行 SQL。
 *
 * <p>只在 sys_user 表为空时执行，所以重启不会重复插入。
 * 密码用 BCrypt 在代码里现算，不写死在 SQL 里。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserRepository sysUserRepository,
                           SysRoleRepository sysRoleRepository,
                           SysMenuRepository sysMenuRepository,
                           PasswordEncoder passwordEncoder) {
        this.sysUserRepository = sysUserRepository;
        this.sysRoleRepository = sysRoleRepository;
        this.sysMenuRepository = sysMenuRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (sysUserRepository.count() > 0) {
            log.info("sys_user 表已有数据，跳过初始数据初始化");
            return;
        }

        log.info("检测到空库，开始初始化 RBAC 基础数据...");

        // ---------- 菜单 ----------
        SysMenu deviceMenu = createMenu("设备管理", SysMenu.ROOT_PARENT_ID, "M",
                "/devices", null, "device:list", "Monitor", 1);
        SysMenu helloMenu = createMenu("联调测试", SysMenu.ROOT_PARENT_ID, "M",
                "/hello", null, "hello:view", "Link", 2);
        sysMenuRepository.saveAll(List.of(deviceMenu, helloMenu));

        // 系统管理是目录，下面挂三个子菜单。
        // 必须先 save 拿到目录的 id，子菜单才能把 parentId 指向它。
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

        // ---------- 角色 ----------
        // 注意先保存角色，再保存引用它的用户。
        // @ManyToMany 没有配 cascade，如果直接拿瞬时态的角色去存用户会报错。
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
        // 只给前两个菜单：operator 登录后侧边栏不会出现"系统管理"。
        // 这就让两个角色的菜单真正有了区别，能直观看出权限过滤在起作用。
        operatorRole.setMenus(new HashSet<>(List.of(deviceMenu, helloMenu)));
        sysRoleRepository.save(operatorRole);

        // ---------- 用户 ----------
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
        log.info("注意：这是开发用弱口令，正式环境务必修改或关闭本初始化器");
    }

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
}
