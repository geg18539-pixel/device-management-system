package com.yan.backend.repository;

import com.yan.backend.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<SysMenu> findAllByOrderBySortOrderAsc();

    boolean existsByMenuNameAndParentId(String menuName, Long parentId);
}
