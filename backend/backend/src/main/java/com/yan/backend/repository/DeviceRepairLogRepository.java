package com.yan.backend.repository;

import com.yan.backend.entity.DeviceRepairLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepairLogRepository extends JpaRepository<DeviceRepairLog, Long> {

    /** 按工单查日志，时间正序（从早到晚读起来顺） */
    List<DeviceRepairLog> findByRepairIdOrderByLogTimeAscIdAsc(Long repairId);

    /** 删除工单时一并删掉它的日志，避免留下悬空记录 */
    void deleteByRepairId(Long repairId);
}
