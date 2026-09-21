package com.yan.backend.repository;

import com.yan.backend.entity.DeviceMaintenancePlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceMaintenancePlanRepository
        extends JpaRepository<DeviceMaintenancePlan, Long>,
        JpaSpecificationExecutor<DeviceMaintenancePlan> {

    /** 一台设备一条计划 */
    Optional<DeviceMaintenancePlan> findByDeviceId(Long deviceId);

    /** 删除设备时清理它的计划（计划属于配置，随设备一起走） */
    boolean existsByDeviceId(Long deviceId);

    /** 列表默认排序：快到期的排前面，这样一进页面就看见该处理的 */
    Page<DeviceMaintenancePlan> findAllByOrderByNextMaintenanceDateAsc(Pageable pageable);

    /**
     * 到期 / 逾期的计划（nextMaintenanceDate ≤ deadline）。
     *
     * <p>只取"启用"状态的计划：停用的计划不参与告警。
     * 返回 List 而不是 Page，是因为调用方（看板告警、页面提示）需要的是
     * "有哪些"而不是"第几页"，而且真正到期的数量天然是有限的。
     */
    List<DeviceMaintenancePlan> findByStatusAndNextMaintenanceDateLessThanEqualOrderByNextMaintenanceDateAsc(
            String status, LocalDate deadline);
}
