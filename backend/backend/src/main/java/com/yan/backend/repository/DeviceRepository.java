package com.yan.backend.repository;

import com.yan.backend.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    /** 该部门下是否还有设备，删除部门前检查 */
    boolean existsByDeptId(Long deptId);

    /** 按设备名找一台「还没归属部门」的设备，用于给演示数据补部门（不会碰用户自己建的设备） */
    Optional<Device> findFirstByDeviceNameAndDeptIdIsNullOrderByIdAsc(String deviceName);

    /**
     * 同部门的**其他**设备。
     *
     * <p>设备关系图用。没有它的话，以一台设备为中心的 1 跳图是一个**只有星形、
     * 没有别的设备节点**的图 —— "点一个设备继续往下走"这个交互就落空了，
     * 用户看完一台只能回选择器里重新搜。加上同部门设备之后，
     * 图才是可导航的：沿着部门走到旁边的设备，再以它为中心展开。
     *
     * <p>用 {@code IdNot} 把自己排除掉，否则中心设备会在图上出现两次。
     * 排序用 id 而不是别的字段：**结果必须是稳定的**，
     * 否则同一台设备每次刷新看到的邻居都不一样，会让人以为数据在变。
     */
    Page<Device> findByDeptIdAndIdNotOrderByIdAsc(Long deptId, Long id, Pageable pageable);

    /**
     * 把生命周期状态为空的设备回填成指定值。
     *
     * <p>为什么需要这个：{@code lifecycle_status} 是后加的列，而
     * {@code ddl-auto=update} **只新增列、不回填数据**，所以升级后老设备这一列全是 NULL。
     * NULL 在看板上会变成一个独立的「未知」分类，把"正常"的数量拆成两半。
     *
     * <p>这里依次调用两次来完成回填：先把"维修中"的设备标成生命周期=维修，
     * 再把剩下的 NULL 统一标成正常。顺序不能反 —— 反了的话第一步就没得可改了。
     */
    @Modifying
    @Query("update Device d set d.lifecycleStatus = :lifecycle "
            + "where d.lifecycleStatus is null and d.status = :status")
    int backfillLifecycleByStatus(@Param("status") String status,
                                  @Param("lifecycle") String lifecycle);

    /** 兜底：把仍然为 NULL 的生命周期状态统一标成正常 */
    @Modifying
    @Query("update Device d set d.lifecycleStatus = :normal where d.lifecycleStatus is null")
    int backfillNullLifecycleStatus(@Param("normal") String normal);

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

    /**
     * 按**生命周期状态**分组统计（正常 / 维修 / 报废 / 停用），看板的设备状态图用。
     *
     * <p>注意和上面的 countGroupByStatus 是两个不同的维度：
     * 那个按"连通性"（在线/离线），这个按"资产状态"。
     */
    @Query("select d.lifecycleStatus, count(d) from Device d group by d.lifecycleStatus")
    List<Object[]> countGroupByLifecycle();

    /** 按归属部门分组统计，看板的部门分布图用。deptId 为 null 的在服务层归为"未分配" */
    @Query("select d.deptId, count(d) from Device d group by d.deptId order by count(d) desc")
    List<Object[]> countGroupByDept();

    /**
     * 按「部门 × 生命周期状态」交叉统计，台账用。
     *
     * <p>返回的每项是 [deptId, lifecycleStatus, count]。
     * 在数据库端做交叉分组，再在服务层透视成"每个部门一行、四种状态各一列"，
     * 比把设备全查出来在 Java 里数要省得多。
     */
    @Query("select d.deptId, d.lifecycleStatus, count(d) from Device d "
            + "group by d.deptId, d.lifecycleStatus")
    List<Object[]> countGroupByDeptAndLifecycle();

    /** 按「分类 × 生命周期状态」交叉统计，台账用 */
    @Query("select d.categoryId, d.lifecycleStatus, count(d) from Device d "
            + "group by d.categoryId, d.lifecycleStatus")
    List<Object[]> countGroupByCategoryAndLifecycle();

    /** 借出中的设备数（borrower 有值即视为借出） */
    long countByBorrowerIsNotNull();

    /**
     * 即将到期（或已过期）维保的设备清单。
     *
     * <p>已报废的设备不算 —— 报废品不需要再安排保养了。
     * 生命周期状态允许为 NULL 是兼容老数据：那一列是后加的，
     * 虽然启动时会回填，但这里仍然写成 NULL 安全的条件。
     */
    @Query("select d from Device d where d.warrantyDate is not null "
            + "and d.warrantyDate <= :deadline "
            + "and (d.lifecycleStatus is null or d.lifecycleStatus <> :scrapped) "
            + "order by d.warrantyDate asc")
    List<Device> findWarrantyExpiring(@Param("deadline") LocalDate deadline,
                                      @Param("scrapped") String scrapped,
                                      Pageable pageable);

    /**
     * 即将到期 / 已过保设备的**总数**（条件同上）。
     *
     * <p>单独一个 count 方法，是因为清单本身有条数上限：
     * 页面提示要写"共 N 台"，直接拿清单长度的话，超过上限时会显示成上限值。
     */
    @Query("select count(d) from Device d where d.warrantyDate is not null "
            + "and d.warrantyDate <= :deadline "
            + "and (d.lifecycleStatus is null or d.lifecycleStatus <> :scrapped)")
    long countWarrantyExpiring(@Param("deadline") LocalDate deadline,
                               @Param("scrapped") String scrapped);
}
