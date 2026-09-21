package com.yan.backend.service;

import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.DeviceRepairQuery;
import com.yan.backend.dto.DeviceRepairStatsVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;

import java.util.List;

public interface DeviceRepairService {

    /** 分页 + 条件筛选 */
    PageResult<DeviceRepair> page(DeviceRepairQuery query);

    /** 导出用：不分页地取出符合条件的工单（上限在实现里控制） */
    List<DeviceRepair> listForExport(DeviceRepairQuery query);

    DeviceRepair findById(Long id);

    // ---------------- 状态流转：待受理 → 维修中 → 已完成 → 已关闭 ----------------

    /**
     * 受理：待受理 → 维修中。
     *
     * <p>记下受理时间，这样"报修后多久有人接单"才是个能算出来的指标。
     */
    DeviceRepair accept(Long id);

    /** 指派 / 改派维修人员。工单已到终态时不能指派 */
    DeviceRepair assign(Long id, String repairer);

    /** 完工：维修中 → 已完成。写维修结果、费用，并把设备状态改回"在线" */
    DeviceRepair finish(Long id, DeviceRepairFinishRequest request);

    /**
     * 关闭：待受理 → 已关闭（作废），或 已完成 → 已关闭（归档）。
     *
     * <p>**维修中不能直接关闭** —— 那会让设备永远停在"维修中"、又没有工单可跟进。
     */
    DeviceRepair close(Long id, String reason);

    void delete(Long id);

    /** 工单统计（统计看板用） */
    DeviceRepairStatsVO stats();

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
