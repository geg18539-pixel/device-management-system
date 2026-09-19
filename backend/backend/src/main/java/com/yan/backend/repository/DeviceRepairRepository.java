package com.yan.backend.repository;

import com.yan.backend.entity.DeviceRepair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepairRepository extends JpaRepository<DeviceRepair, Long> {

    Page<DeviceRepair> findByDeviceIdOrderByReportTimeDesc(Long deviceId, Pageable pageable);

    Page<DeviceRepair> findByRepairStatusOrderByReportTimeDesc(String repairStatus, Pageable pageable);

    Page<DeviceRepair> findAllByOrderByReportTimeDesc(Pageable pageable);

    /** 该设备是否还有未完工的工单 —— 用于"维修中的设备不能借用"之类的判断 */
    List<DeviceRepair> findByDeviceIdAndRepairStatusNot(Long deviceId, String repairStatus);

    /** 全部未完工的工单，给 AI 的数据快照用 */
    Page<DeviceRepair> findByRepairStatusNotOrderByReportTimeDesc(String repairStatus, Pageable pageable);

    /** 删除设备前检查是否有关联工单，避免留下悬空的 deviceId */
    boolean existsByDeviceId(Long deviceId);
}
