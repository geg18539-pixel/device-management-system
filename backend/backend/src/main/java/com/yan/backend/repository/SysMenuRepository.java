package com.yan.backend.repository;

import com.yan.backend.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<SysMenu> findAllByOrderBySortOrderAsc();

    boolean existsByMenuNameAndParentId(String menuName, Long parentId);

    /** 判断某个菜单下是否还有子菜单，删除前要检查 */
    boolean existsByParentId(Long parentId);

    /** 该权限标识是否已被别的菜单/按钮占用 */
    boolean existsByPerms(String perms);

    /** 是否已经种过按钮权限，用于种子数据的幂等判断 */
    boolean existsByMenuType(String menuType);

    /**
     * 算出某个用户拥有的全部权限标识。
     *
     * <p>路径是 用户 →(多对多)→ 角色 →(多对多)→ 菜单/按钮，取其中非空的 perms。
     * 用一条 JPQL 走完，不要在 Java 里逐层遍历 —— 那是典型的 N+1。
     *
     * <p>只统计**状态正常**的角色：给用户停用某个角色后，该角色带来的权限应当立刻失效。
     */
    @Query("""
            select distinct m.perms from SysUser u
            join u.roles r
            join r.menus m
            where u.id = :userId
              and r.status = '正常'
              and m.perms is not null
              and m.perms <> ''
            """)
    List<String> findPermsByUserId(@Param("userId") Long userId);
}
