package com.yan.backend.service;

import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;

public interface DeviceRepairService {

    /** 分页查询维修工单。repairStatus / deviceId 都可为空表示不筛选 */
    PageResult<DeviceRepair> page(int pageNum, int pageSize, String repairStatus, Long deviceId);

    DeviceRepair findById(Long id);

    /** 工单完工，同时把设备状态从"维修中"改回"在线" */
    DeviceRepair finish(Long id, DeviceRepairFinishRequest request);

    void delete(Long id);
}
