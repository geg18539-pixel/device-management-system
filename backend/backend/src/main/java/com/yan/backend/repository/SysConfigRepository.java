package com.yan.backend.repository;

import com.yan.backend.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);

    /** 全部参数，按分组和排序号排好，页面直接用 */
    List<SysConfig> findAllByOrderByConfigGroupAscSortOrderAscIdAsc();
}
