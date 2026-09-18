package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceRepairService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class DeviceRepairServiceImpl implements DeviceRepairService {

    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepository deviceRepository;

    public DeviceRepairServiceImpl(DeviceRepairRepository deviceRepairRepository,
                                   DeviceRepository deviceRepository) {
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepository = deviceRepository;
    }

    @Override
    public PageResult<DeviceRepair> page(int pageNum, int pageSize, String repairStatus, Long deviceId) {
        Pageable pageable = PageRequest.of(Math.max(pageNum, 1) - 1, pageSize);

        Page<DeviceRepair> page;
        if (deviceId != null) {
            page = deviceRepairRepository.findByDeviceIdOrderByReportTimeDesc(deviceId, pageable);
        } else if (repairStatus != null && !repairStatus.isBlank()) {
            page = deviceRepairRepository.findByRepairStatusOrderByReportTimeDesc(repairStatus, pageable);
        } else {
            page = deviceRepairRepository.findAllByOrderByReportTimeDesc(pageable);
        }
        return PageResult.of(page);
    }

    @Override
    public DeviceRepair findById(Long id) {
        return deviceRepairRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("维修工单不存在，id = " + id));
    }

    @Override
    @Transactional
    public DeviceRepair finish(Long id, DeviceRepairFinishRequest request) {
        DeviceRepair repair = findById(id);

        if (DeviceRepair.STATUS_FINISHED.equals(repair.getRepairStatus())) {
            throw new IllegalStateException("该工单已完成，不能重复完工");
        }

        repair.setRepairStatus(DeviceRepair.STATUS_FINISHED);
        repair.setRepairer(request.getRepairer());
        repair.setCost(request.getCost());
        repair.setFinishTime(LocalDateTime.now());
        if (request.getRemark() != null) {
            repair.setRemark(request.getRemark());
        }
        deviceRepairRepository.save(repair);

        // 联动设备状态。
        // 只在该设备当前确实处于"维修中"时才改回"在线" ——
        // 否则会把用户后来手动改过的状态覆盖掉（比如设备已经报废了）。
        deviceRepository.findById(repair.getDeviceId()).ifPresent(device -> {
            if (Device.STATUS_REPAIRING.equals(device.getStatus())) {
                device.setStatus(Device.STATUS_ONLINE);
                deviceRepository.save(device);
            }
        });

        return repair;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DeviceRepair repair = findById(id);

        // 未完工的工单不允许删，否则设备会永远停在"维修中"却找不到对应工单
        if (!DeviceRepair.STATUS_FINISHED.equals(repair.getRepairStatus())) {
            throw new IllegalStateException("工单尚未完成，不能删除");
        }

        deviceRepairRepository.deleteById(id);
    }
}
