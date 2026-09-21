package com.yan.backend.repository;

import com.yan.backend.entity.SysDictItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SysDictItemRepository extends JpaRepository<SysDictItem, Long> {

    /** 某个字典下的全部项（含停用的），后台管理页用 */
    List<SysDictItem> findByDictTypeOrderBySortOrderAscIdAsc(String dictType);

    /** 某个字典下**启用中**的项，给下拉用。停用的项不该出现在可选列表里 */
    List<SysDictItem> findByDictTypeAndStatusOrderBySortOrderAscIdAsc(String dictType, String status);

    /** 该字典下是否还有项，删除字典类型前检查 */
    boolean existsByDictType(String dictType);

    /** 同一字典下键值不能重复 */
    boolean existsByDictTypeAndItemValue(String dictType, String itemValue);

    void deleteByDictType(String dictType);

    long countByDictType(String dictType);
}
