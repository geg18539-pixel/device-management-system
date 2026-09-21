package com.yan.backend.repository;

import com.yan.backend.entity.SparePart;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SparePartRepository
        extends JpaRepository<SparePart, Long>, JpaSpecificationExecutor<SparePart> {

    Optional<SparePart> findByPartCode(String partCode);

    boolean existsByPartCode(String partCode);

    /**
     * 带**悲观写锁**地按 id 取配件，出入库时用。
     *
     * <p>为什么必须加锁：出库的判断是"读当前库存 → 比较 → 写回"，
     * 两个并发请求（两个人同时给同一张工单领料）可能都读到 5、都判定够用、
     * 各自扣 3，最后库存变成 2 —— 实际该是 -1，凭空多出 1 个。
     * 加 SELECT ... FOR UPDATE 让后一个请求等前一个提交，读到的是扣减后的值。
     *
     * <p>库存是这个系统里为数不多"并发真的会出问题"的地方，
     * 其他 CRUD 都是各改各的行，不需要锁。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SparePart p where p.id = :id")
    Optional<SparePart> findByIdForUpdate(@Param("id") Long id);

    Page<SparePart> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 库存告急的配件（库存 ≤ 预警阈值）。
     *
     * <p>两个字段都在同一张表上，所以直接比较即可，不用 join。
     * 只在"启用"状态的配件里找 —— 停用的配件不再补货，不参与预警。
     */
    @Query("select p from SparePart p "
            + "where p.status = :enabled and p.stockQuantity <= p.warnThreshold "
            + "order by p.stockQuantity asc")
    List<SparePart> findLowStock(String enabled);

    /** 库存告急数量，看板用 */
    @Query("select count(p) from SparePart p "
            + "where p.status = :enabled and p.stockQuantity <= p.warnThreshold")
    long countLowStock(String enabled);
}
