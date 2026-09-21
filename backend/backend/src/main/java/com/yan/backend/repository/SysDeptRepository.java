package com.yan.backend.repository;

import com.yan.backend.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findAllByOrderBySortOrderAscIdAsc();

    /** 删除前检查是否还有子部门 */
    boolean existsByParentId(Long parentId);

    /** 同级下是否重名 */
    boolean existsByDeptNameAndParentId(String deptName, Long parentId);

    /**
     * 按「名称 + 上级」查部门，种子数据用它做逐条幂等。
     *
     * <p>加 parentId 是必须的：不同上级下允许有同名部门
     * （比如"运维部"可以同时挂在总公司和某个分公司下面），
     * 只按名称查会在有同名数据时抛 IncorrectResultSizeDataAccessException。
     */
    Optional<SysDept> findByDeptNameAndParentId(String deptName, Long parentId);
}
