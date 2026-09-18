package com.yan.backend.repository;

import com.yan.backend.entity.DeviceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceCategoryRepository extends JpaRepository<DeviceCategory, Long> {

    List<DeviceCategory> findAllByOrderBySortOrderAsc();

    List<DeviceCategory> findByParentIdOrderBySortOrderAsc(Long parentId);

    /** 删除前检查是否有子分类，避免留下挂在树外面的孤儿节点 */
    boolean existsByParentId(Long parentId);

    boolean existsByCategoryNameAndParentId(String categoryName, Long parentId);
}
