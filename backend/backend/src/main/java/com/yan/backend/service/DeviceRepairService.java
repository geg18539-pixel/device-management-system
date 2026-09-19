package com.yan.backend.service;

import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;

import java.util.List;

public interface DeviceRepairService {

    /** 分页查询维修工单。repairStatus / deviceId 都可为空表示不筛选 */
    PageResult<DeviceRepair> page(int pageNum, int pageSize, String repairStatus, Long deviceId);

    DeviceRepair findById(Long id);

    /** 工单完工，同时把设备状态从"维修中"改回"在线"，并写入一条状态变更日志 */
    DeviceRepair finish(Long id, DeviceRepairFinishRequest request);

    void delete(Long id);

    // ---------------- 维修日志 ----------------

    /** 查某张工单的全部日志，按时间正序 */
    List<DeviceRepairLog> listLogs(Long repairId);

    /** 手工追加一条维修记录。日志只增不改，没有对应的修改和删除接口 */
    DeviceRepairLog addLog(Long repairId, String content);

    // ---------------- AI 分析 ----------------

    /**
     * 重新触发 AI 分析。
     *
     * <p>用在两种场景：分析失败后重试；或者故障描述被补充过、想重新分析一次。
     * 内部还是异步执行，接口立即返回。
     */
    void reanalyze(Long repairId);
}
