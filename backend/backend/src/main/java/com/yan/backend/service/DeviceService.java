package com.yan.backend.service;

import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceLedgerVO;
import com.yan.backend.dto.DeviceQuery;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceScrapRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.DeviceTransferRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceTransfer;

import java.util.List;

public interface DeviceService {

    /** 查全部设备（不分页）。给需要一次性拿全量的场景用，比如下拉选择 */
    List<Device> findAll();

    /**
     * 分页 + 条件筛选。
     *
     * <p>条件收在 {@link DeviceQuery} 里而不是一串方法参数：维度已经有 7 个，
     * 平铺成参数后调用方分不清哪个 Long 是分类、哪个是部门。
     */
    PageResult<Device> page(DeviceQuery query);

    /** 导出用：不分页地取出符合条件的设备（上限在实现里控制） */
    List<Device> listForExport(DeviceQuery query);

    /** 设备台账汇总（按部门、按分类、按生命周期状态） */
    DeviceLedgerVO ledger();

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

    // ---------------- 调拨 / 报废 ----------------

    /** 某台设备的调拨历史，最近的在前 */
    List<DeviceTransfer> findTransfers(Long deviceId);

    /**
     * 调拨：把设备改到目标部门，并追加一条调拨记录。
     *
     * <p>设备归属部门**只能通过这个接口改** —— 编辑表单不再直接改 deptId。
     * 否则"调拨历史"会漏掉那些从表单改掉的变更，审计链就断了。
     */
    Device transfer(Long id, DeviceTransferRequest request);

    /** 报废：状态改成"报废"并记录时间/原因/操作人 */
    Device scrap(Long id, DeviceScrapRequest request);

    /** 取消报废（误操作的回退口子），状态改回"正常"并清空报废信息 */
    Device restore(Long id);

    /** 图表统计数据：状态分布 + 分类统计 */
    DeviceStatsVO stats();
}
