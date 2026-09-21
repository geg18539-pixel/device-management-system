package com.yan.backend.repository;

import com.yan.backend.entity.DeviceMaintenanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceMaintenanceRecordRepository
        extends JpaRepository<DeviceMaintenanceRecord, Long>,
        JpaSpecificationExecutor<DeviceMaintenanceRecord> {

    /** 某台设备的维保记录（设备详情页用），最近的在前，不分页 */
    List<DeviceMaintenanceRecord> findByDeviceIdOrderByMaintenanceDateDesc(Long deviceId);

    /** 同上，但分页 —— 维保记录列表页按设备筛选时用 */
    Page<DeviceMaintenanceRecord> findByDeviceIdOrderByMaintenanceDateDesc(Long deviceId,
                                                                          Pageable pageable);

    /** 维保记录列表页 */
    Page<DeviceMaintenanceRecord> findAllByOrderByMaintenanceDateDesc(Pageable pageable);

    /** 删除设备前的保护检查：有维保记录就不给删 */
    boolean existsByDeviceId(Long deviceId);

    /** 某条计划下已产生了多少条记录 —— 删除计划前的检查 */
    long countByPlanId(Long planId);
}
