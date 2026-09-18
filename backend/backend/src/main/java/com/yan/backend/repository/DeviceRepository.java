package com.yan.backend.repository;

import com.yan.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 设备数据访问层。
 *
 * <p>除了 JpaRepository 的基础方法，额外继承了 JpaSpecificationExecutor ——
 * 5.5 的列表要支持"按分类 + 按状态"任意组合筛选，用 Specification 动态拼条件
 * 比写一堆 findByCategoryIdAndStatus... 的组合方法清晰得多，也不用为每种
 * 组合都声明一个方法。
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    boolean existsByAssetCode(String assetCode);

    boolean existsBySerialNumber(String serialNumber);

    /** 该分类下是否还有设备，删除分类前检查 */
    boolean existsByCategoryId(Long categoryId);

    /**
     * 按状态分组统计，给饼图用。
     *
     * <p>返回 List&lt;Object[]&gt;，每项是 [status, count]。
     * 在数据库端 group by，不要把全部设备查出来在 Java 里数 —— 数据量一大
     * 就是全表扫描加内存聚合。
     */
    @Query("select d.status, count(d) from Device d group by d.status order by count(d) desc")
    List<Object[]> countGroupByStatus();

    /** 按分类分组统计，给柱状图用。categoryId 为 null 的在服务层归为"未分类" */
    @Query("select d.categoryId, count(d) from Device d group by d.categoryId order by count(d) desc")
    List<Object[]> countGroupByCategory();
}
