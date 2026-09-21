package com.yan.backend.repository;

import com.yan.backend.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    /**
     * 按用户名查用户，并把角色一起查出来。
     *
     * <p>必须用 @EntityGraph 预取 roles。因为 application.yml 里设了
     * open-in-view=false，事务结束后 Session 就关了，在事务外访问
     * user.getRoles() 会抛 LazyInitializationException。
     */
    @EntityGraph(attributePaths = "roles")
    Optional<SysUser> findByUsername(String username);

    /**
     * 分页查询，按用户名模糊匹配。不预取 roles，交给 Service 在事务内按需加载。
     *
     * <p>这是"只按用户名搜"的旧方法，保留给简单场景；多条件组合筛选走 Specification。
     */
    Page<SysUser> findByUsernameContaining(String username, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByRoles_Id(Long roleId);

    /** 该部门下是否还有用户，删除部门前检查 */
    boolean existsByDeptId(Long deptId);

    /** 批量操作时一次把要处理的用户查出来，避免在循环里逐条 findById */
    List<SysUser> findByIdIn(Collection<Long> ids);

    /**
     * 找"该收到这类告警"的启用账号。
     *
     * <h3>为什么不是"发给所有人"</h3>
     *
     * <p>库存告急和设备健康预警都**没有归属人** —— 不像维保计划有 maintainer 字段
     * 可以精确推送。但也不该广播给全体用户：设备健康是运维职责范围内的事，
     * 发给不相关的人只会让消息中心变成一个没人看的红点。
     * 所以口径是**"谁能处理就通知谁"**，按权限点筛。
     *
     * <h3>⚠️ 为什么必须显式带上 admin 角色</h3>
     *
     * <p><b>超管是绕过权限点的</b>（见 {@code JwtInterceptor.SUPER_ADMIN_ROLE}）——
     * 它不走"角色 → 菜单 → 权限标识"这条链，所以 admin 角色的用户
     * **在 sys_role_menu 里可能压根没有这些权限点的记录**。
     * 只按 {@code m.perms in :perms} 筛的话，管理员反而收不到告警，
     * 而管理员恰恰是最该知道的人。这一条是实测踩出来的。
     *
     * <h3>关于 left join</h3>
     *
     * <p>用 left join 而不是 inner join：`r.menus` 为空的角色（新建还没授权）也要能被
     * 第一个条件（admin 角色）选出来。inner join 会让这种角色整个消失。
     *
     * @param enabledStatus "正常"（启用）的账号才收告警，停用账号收不到
     * @param adminRole     超管角色标识，这里和 {@code JwtInterceptor} 保持一致
     * @param perms         能处理这类告警的人所拥有的权限点。**不能传空集合** ——
     *                      JPQL 会生成 `in ()` 这种非法语法直接报错
     */
    @Query("select distinct u from SysUser u join u.roles r left join r.menus m "
            + "where u.status = :enabledStatus "
            + "and (r.roleKey = :adminRole or m.perms in :perms)")
    List<SysUser> findAlertRecipients(@Param("enabledStatus") String enabledStatus,
                                      @Param("adminRole") String adminRole,
                                      @Param("perms") Collection<String> perms);
}
