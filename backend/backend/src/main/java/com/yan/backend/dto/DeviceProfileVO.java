package com.yan.backend.dto;

import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceAttachment;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceMaintenanceRecord;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceTransfer;
import com.yan.backend.entity.SparePartRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备档案详情（设备详情页一次性要的全部内容）。
 *
 * <p>一个接口返回六个区块而不是六个接口，理由和看板一样：
 * 详情页是"打开就要看全"的页面，拆成六个请求会让各区块先后闪现，
 * 而且用户的网络往返次数白白翻了六倍。
 *
 * <p>{@code maintenancePlan} 是单个而不是列表：一台设备只允许有一条维保计划
 * （允许多条的话"下次该什么时候保养"就没有唯一答案）。
 */
public class DeviceProfileVO {

    /** 基础信息 */
    private Device device;

    /** 维保计划。没有则为 null */
    private DeviceMaintenancePlan maintenancePlan;

    /** 维保记录（按保养日期倒序） */
    private List<DeviceMaintenanceRecord> maintenanceRecords = new ArrayList<>();

    /** 维修工单历史（按报修时间倒序） */
    private List<DeviceRepair> repairHistory = new ArrayList<>();

    /** 配件更换记录：由该设备的工单反查出的出库流水 */
    private List<SparePartRecord> partRecords = new ArrayList<>();

    /** 附件 */
    private List<DeviceAttachment> attachments = new ArrayList<>();

    /** 调拨记录（按时间倒序） */
    private List<DeviceTransfer> transfers = new ArrayList<>();

    // ---------- getter / setter ----------

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public DeviceMaintenancePlan getMaintenancePlan() {
        return maintenancePlan;
    }

    public void setMaintenancePlan(DeviceMaintenancePlan maintenancePlan) {
        this.maintenancePlan = maintenancePlan;
    }

    public List<DeviceMaintenanceRecord> getMaintenanceRecords() {
        return maintenanceRecords;
    }

    public void setMaintenanceRecords(List<DeviceMaintenanceRecord> maintenanceRecords) {
        this.maintenanceRecords = maintenanceRecords;
    }

    public List<DeviceRepair> getRepairHistory() {
        return repairHistory;
    }

    public void setRepairHistory(List<DeviceRepair> repairHistory) {
        this.repairHistory = repairHistory;
    }

    public List<SparePartRecord> getPartRecords() {
        return partRecords;
    }

    public void setPartRecords(List<SparePartRecord> partRecords) {
        this.partRecords = partRecords;
    }

    public List<DeviceAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<DeviceAttachment> attachments) {
        this.attachments = attachments;
    }

    public List<DeviceTransfer> getTransfers() {
        return transfers;
    }

    public void setTransfers(List<DeviceTransfer> transfers) {
        this.transfers = transfers;
    }
}
