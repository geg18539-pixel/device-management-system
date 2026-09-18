package com.yan.backend.service;

import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;

import java.util.List;

public interface DeviceService {

    /** 查全部设备（不分页）。给需要一次性拿全量的场景用，比如下拉选择 */
    List<Device> findAll();

    /**
     * 分页 + 条件筛选。
     *
     * @param categoryId 按分类筛选，null 表示不限
     * @param status     按状态筛选，null 或空表示不限
     * @param keyword    按设备名称 / 资产编号 / 序列号模糊匹配，null 或空表示不限
     */
    PageResult<Device> page(int pageNum, int pageSize, Long categoryId, String status, String keyword);

    Device findById(Long id);

    Device save(Device device);

    Device update(Long id, Device device);

    void delete(Long id);

    /** 借用：记录借用人、借出时间，状态改为"使用中" */
    Device borrow(Long id, DeviceBorrowRequest request);

    /** 归还：清空借用信息，状态改回"在线" */
    Device giveBack(Long id);

    /** 报修：生成维修工单，状态改为"维修中" */
    Device reportRepair(Long id, DeviceRepairRequest request);

    /** 图表统计数据：状态分布 + 分类统计 */
    DeviceStatsVO stats();
}
