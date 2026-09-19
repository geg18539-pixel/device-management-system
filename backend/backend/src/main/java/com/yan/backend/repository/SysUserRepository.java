package com.yan.backend.repository;

import com.yan.backend.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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

    /** 批量操作时一次把要处理的用户查出来，避免在循环里逐条 findById */
    List<SysUser> findByIdIn(Collection<Long> ids);
}
