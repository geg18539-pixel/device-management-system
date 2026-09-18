package com.yan.backend.repository;

import com.yan.backend.entity.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    /**
     * 按用户名查用户，并把角色一起查出来。
     *
     * <p>必须用 @EntityGraph 预取 roles。因为 application.yml 里设了
     * open-in-view=false，事务结束后 Session 就关了，在事务外访问
     * user.getRoles() 会抛 LazyInitializationException。
     * 加上 EntityGraph 之后，Spring Data 会生成 LEFT JOIN 把角色一次带出来。
     */
    @EntityGraph(attributePaths = "roles")
    Optional<SysUser> findByUsername(String username);

    /** 分页查询，按用户名模糊匹配。不预取 roles，交给 Service 在事务内按需加载。 */
    Page<SysUser> findByUsernameContaining(String username, Pageable pageable);

    boolean existsByUsername(String username);

    boolean existsByRoles_Id(Long roleId);
}
