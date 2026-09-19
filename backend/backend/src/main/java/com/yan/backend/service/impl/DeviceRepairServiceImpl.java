package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairLogRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.AiFaultAnalysisService;
import com.yan.backend.service.DeviceRepairService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeviceRepairServiceImpl implements DeviceRepairService {

    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepairLogRepository deviceRepairLogRepository;
    private final DeviceRepository deviceRepository;
    private final AiFaultAnalysisService aiFaultAnalysisService;

    public DeviceRepairServiceImpl(DeviceRepairRepository deviceRepairRepository,
                                   DeviceRepairLogRepository deviceRepairLogRepository,
                                   DeviceRepository deviceRepository,
                                   AiFaultAnalysisService aiFaultAnalysisService) {
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepairLogRepository = deviceRepairLogRepository;
        this.deviceRepository = deviceRepository;
        this.aiFaultAnalysisService = aiFaultAnalysisService;
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

        // 状态流转自动留一条日志，这样维修历史是完整的、不需要人工补记
        StringBuilder content = new StringBuilder("工单已完成，维修人：").append(request.getRepairer());
        if (request.getCost() != null) {
            content.append("，费用：").append(request.getCost());
        }
        if (request.getRemark() != null && !request.getRemark().isBlank()) {
            content.append("，说明：").append(request.getRemark());
        }
        writeLog(repair.getId(), DeviceRepairLog.TYPE_STATUS, content.toString(),
                request.getRepairer(), LocalDateTime.now());

        // 联动设备状态。
        // 只在该设备当前确实是"维修中"时才改回"在线"，
        // 否则会覆盖用户后来手动改过的状态（比如设备已经报废了）。
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

        // 日志跟着工单一起删，避免留下指向不存在工单的悬空记录
        deviceRepairLogRepository.deleteByRepairId(id);
        deviceRepairRepository.deleteById(id);
    }

    // ---------------- 维修日志 ----------------

    @Override
    public List<DeviceRepairLog> listLogs(Long repairId) {
        // 先确认工单存在，否则查一个不存在的 id 会静默返回空列表，让人以为"日志丢了"
        findById(repairId);
        return deviceRepairLogRepository.findByRepairIdOrderByLogTimeAscIdAsc(repairId);
    }

    @Override
    @Transactional
    public DeviceRepairLog addLog(Long repairId, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("日志内容不能为空");
        }
        findById(repairId);

        // 操作人从登录态取，不让前端传 —— 否则可以伪造"是谁记的这条"
        return writeLog(repairId, DeviceRepairLog.TYPE_NOTE, content.trim(),
                UserContext.getUsername(), LocalDateTime.now());
    }

    // ---------------- AI 分析 ----------------

    @Override
    public void reanalyze(Long repairId) {
        findById(repairId);
        // 跨 bean 调用，@Async 才能真正生效（同类自调用不走代理）
        aiFaultAnalysisService.analyzeAsync(repairId);
    }

    // ---------------- 私有辅助 ----------------

    private DeviceRepairLog writeLog(Long repairId, String logType, String content,
                                     String operator, LocalDateTime logTime) {
        DeviceRepairLog entry = new DeviceRepairLog();
        entry.setRepairId(repairId);
        entry.setLogType(logType);
        entry.setContent(content);
        entry.setOperator(operator);
        entry.setLogTime(logTime);
        return deviceRepairLogRepository.save(entry);
    }
}
