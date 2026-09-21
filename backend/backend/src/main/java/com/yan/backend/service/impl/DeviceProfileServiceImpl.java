package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceProfileVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceAttachmentRepository;
import com.yan.backend.repository.DeviceMaintenancePlanRepository;
import com.yan.backend.repository.DeviceMaintenanceRecordRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.DeviceTransferRepository;
import com.yan.backend.repository.SparePartRecordRepository;
import com.yan.backend.service.DeviceProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备档案详情的聚合查询。
 *
 * <p>单独一个 Service 而不是塞进 DeviceServiceImpl：那边已经有十几个依赖了，
 * 再加三个只是为了详情页的查询，会让"设备增删改"和"档案聚合"两件不相干的事
 * 耦在一起。这里只读、只做拼装，职责很单一。
 */
@Service
@Transactional(readOnly = true)
public class DeviceProfileServiceImpl implements DeviceProfileService {

    private final DeviceRepository deviceRepository;
    private final DeviceMaintenancePlanRepository planRepository;
    private final DeviceMaintenanceRecordRepository maintenanceRecordRepository;
    private final DeviceRepairRepository repairRepository;
    private final SparePartRecordRepository partRecordRepository;
    private final DeviceAttachmentRepository attachmentRepository;
    private final DeviceTransferRepository transferRepository;

    public DeviceProfileServiceImpl(DeviceRepository deviceRepository,
                                    DeviceMaintenancePlanRepository planRepository,
                                    DeviceMaintenanceRecordRepository maintenanceRecordRepository,
                                    DeviceRepairRepository repairRepository,
                                    SparePartRecordRepository partRecordRepository,
                                    DeviceAttachmentRepository attachmentRepository,
                                    DeviceTransferRepository transferRepository) {
        this.deviceRepository = deviceRepository;
        this.planRepository = planRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.repairRepository = repairRepository;
        this.partRecordRepository = partRecordRepository;
        this.attachmentRepository = attachmentRepository;
        this.transferRepository = transferRepository;
    }

    @Override
    public DeviceProfileVO profile(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在，id = " + deviceId));

        DeviceProfileVO vo = new DeviceProfileVO();
        vo.setDevice(device);

        // 维保计划：一台设备最多一条，没有就是没有
        planRepository.findByDeviceId(deviceId).ifPresent(vo::setMaintenancePlan);

        vo.setMaintenanceRecords(
                maintenanceRecordRepository.findByDeviceIdOrderByMaintenanceDateDesc(deviceId));

        List<DeviceRepair> repairs =
                repairRepository.findByDeviceIdOrderByReportTimeDesc(deviceId);
        vo.setRepairHistory(repairs);

        // 配件更换记录：配件流水是挂在**工单**上的，所以要先拿到这台设备的工单 id，
        // 再用一次 IN 查询把所有流水捞回来。
        // 不能一张工单一次查询 —— 报修多的设备会产生几十次往返。
        if (repairs.isEmpty()) {
            vo.setPartRecords(List.of());
        } else {
            List<Long> repairIds = repairs.stream().map(DeviceRepair::getId).toList();
            vo.setPartRecords(partRecordRepository
                    .findByRelatedRepairIdInOrderByRecordTimeDesc(repairIds));
        }

        vo.setAttachments(attachmentRepository.findByDeviceIdOrderByUploadTimeDesc(deviceId));
        vo.setTransfers(transferRepository.findByDeviceIdOrderByTransferTimeDesc(deviceId));

        return vo;
    }
}
