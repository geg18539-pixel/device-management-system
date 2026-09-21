package com.yan.backend.repository;

import com.yan.backend.entity.SparePartRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SparePartRecordRepository
        extends JpaRepository<SparePartRecord, Long>,
        JpaSpecificationExecutor<SparePartRecord> {

    /** 全部流水（出入库记录页），最近的在前 */
    Page<SparePartRecord> findAllByOrderByRecordTimeDesc(Pageable pageable);

    /** 某个配件的流水 */
    Page<SparePartRecord> findByPartIdOrderByRecordTimeDesc(Long partId, Pageable pageable);

    /**
     * 某张维修工单消耗的配件。
     *
     * <p>这是"工单关联消耗配件"的落点：出库时把 relatedRepairId 填上，
     * 这里就能反查出一张工单用掉了哪些件。
     */
    List<SparePartRecord> findByRelatedRepairIdOrderByRecordTimeDesc(Long relatedRepairId);

    /**
     * 一批工单消耗的配件（设备详情页用）。
     *
     * <p>为什么要"一批"：设备 → 它的多张工单 → 这些工单消耗的配件。
     * 先查工单 id 列表再用 IN 一次查出配件，比在循环里逐张工单查要少很多次查询。
     */
    List<SparePartRecord> findByRelatedRepairIdInOrderByRecordTimeDesc(
            Collection<Long> relatedRepairIds);

    /** 某配件已有多少条流水 —— 删除配件前的检查 */
    long countByPartId(Long partId);
}
