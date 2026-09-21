package com.yan.backend.service;

import com.yan.backend.dto.MaintenanceExecuteRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceMaintenanceRecord;

import java.util.List;

public interface DeviceMaintenanceService {

    /** 维保计划分页。status / keyword 都可以为空表示不筛 */
    PageResult<DeviceMaintenancePlan> pagePlans(String status, String keyword,
                                                int pageNum, int pageSize);

    /** 某台设备的维保计划（设备详情页用），没有则返回空列表 */
    List<DeviceMaintenancePlan> listPlansByDevice(Long deviceId);

    /** 按 id 取计划，不存在抛 404 */
    DeviceMaintenancePlan findPlan(Long id);

    DeviceMaintenancePlan createPlan(DeviceMaintenancePlan plan);

    DeviceMaintenancePlan updatePlan(Long id, DeviceMaintenancePlan plan);

    /** 删除计划。有计划下已有维保记录时会拒绝（改成停用更合适） */
    void deletePlan(Long id);

    /**
     * 执行一次维保：写记录 + 推进计划的下次到期日。
     *
     * @param planId 计划 id。传 null 表示一次不挂计划的临时保养（只写记录）
     */
    DeviceMaintenanceRecord execute(Long planId, Long deviceId, MaintenanceExecuteRequest request);

    /** 维保记录分页。deviceId 为空表示查全部 */
    PageResult<DeviceMaintenanceRecord> pageRecords(Long deviceId, int pageNum, int pageSize);

    /** 某台设备的全部维保记录（设备详情页用） */
    List<DeviceMaintenanceRecord> listRecordsByDevice(Long deviceId);

    /**
     * 即将到期 / 已逾期的计划（含只剩 warnDays 天的）。
     *
     * <p>会**排除已报废设备**的计划 —— 报废的设备不需要再保养了。
     */
    List<DeviceMaintenancePlan> listDue(int warnDays);

    /** 即将到期 / 已逾期的计划数量 */
    long countDue(int warnDays);
}
