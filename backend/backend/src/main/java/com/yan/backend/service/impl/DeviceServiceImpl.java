package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.ChartItemVO;
import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.event.RepairCreatedEvent;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepairLogRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DeviceServiceImpl implements DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepairLogRepository deviceRepairLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DeviceServiceImpl(DeviceRepository deviceRepository,
                             DeviceCategoryRepository deviceCategoryRepository,
                             DeviceRepairRepository deviceRepairRepository,
                             DeviceRepairLogRepository deviceRepairLogRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepairLogRepository = deviceRepairLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Device> findAll() {
        return deviceRepository.findAll();
    }

    @Override
    public PageResult<Device> page(int pageNum, int pageSize, Long categoryId,
                                   String status, String keyword) {

        // 用 Specification 动态拼条件。
        // 比写 findByCategoryIdAndStatusAndDeviceNameContaining... 那一堆组合方法清晰得多，
        // 而且以后加筛选维度不用新增接口方法。
        Specification<Device> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                // 名称、资产编号、序列号任意一个匹配即可
                predicates.add(cb.or(
                        cb.like(root.get("deviceName"), like),
                        cb.like(root.get("assetCode"), like),
                        cb.like(root.get("serialNumber"), like)
                ));
            }
            // 条件全为空时 cb.and() 返回恒真谓词，等价于不筛选
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize, Sort.by(Sort.Direction.ASC, "id"));

        Page<Device> page = deviceRepository.findAll(spec, pageable);
        return PageResult.of(page);
    }

    @Override
    public Device findById(Long id) {
        return getDevice(id);
    }

    @Override
    @Transactional
    public Device save(Device device) {
        device.setId(null);
        checkAssetCodeUnique(device.getAssetCode(), null);
        checkSerialNumberUnique(device.getSerialNumber(), null);
        return deviceRepository.save(device);
    }

    @Override
    @Transactional
    public Device update(Long id, Device device) {
        Device existing = getDevice(id);

        checkAssetCodeUnique(device.getAssetCode(), id);
        checkSerialNumberUnique(device.getSerialNumber(), id);

        existing.setDeviceName(device.getDeviceName());
        existing.setDeviceType(device.getDeviceType());
        existing.setCategoryId(device.getCategoryId());
        existing.setAssetCode(device.getAssetCode());
        existing.setSerialNumber(device.getSerialNumber());
        existing.setStatus(device.getStatus());
        existing.setLocation(device.getLocation());
        existing.setDescription(device.getDescription());
        existing.setPurchaseDate(device.getPurchaseDate());
        existing.setWarrantyDate(device.getWarrantyDate());

        // 借用信息不在这里改 —— 那是 borrow / giveBack 的职责，
        // 编辑表单不该能把"谁借着"改掉
        return deviceRepository.save(existing);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Device device = getDevice(id);

        if (device.getBorrower() != null && !device.getBorrower().isBlank()) {
            throw new IllegalStateException("设备已被「" + device.getBorrower() + "」借出，不能删除");
        }
        // 有维修历史就不给删。工单里存的是 device_id，
        // 设备删了工单就变成指向不存在记录的悬空引用
        if (deviceRepairRepository.existsByDeviceId(id)) {
            throw new IllegalStateException("该设备存在维修工单记录，不能删除");
        }

        deviceRepository.deleteById(id);
    }

    // ---------------- 借用 / 归还 ----------------

    @Override
    @Transactional
    public Device borrow(Long id, DeviceBorrowRequest request) {
        Device device = getDevice(id);

        if (Device.STATUS_REPAIRING.equals(device.getStatus())) {
            throw new IllegalStateException("设备正在维修中，不能借用");
        }
        if (device.getBorrower() != null && !device.getBorrower().isBlank()) {
            throw new IllegalStateException("设备已被「" + device.getBorrower() + "」借出，请先归还");
        }

        device.setBorrower(request.getBorrower());
        device.setBorrowTime(LocalDateTime.now());
        device.setStatus(Device.STATUS_IN_USE);
        return deviceRepository.save(device);
    }

    @Override
    @Transactional
    public Device giveBack(Long id) {
        Device device = getDevice(id);

        if (device.getBorrower() == null || device.getBorrower().isBlank()) {
            throw new IllegalStateException("该设备当前没有被借出");
        }

        device.setBorrower(null);
        device.setBorrowTime(null);
        device.setStatus(Device.STATUS_ONLINE);
        return deviceRepository.save(device);
    }

    // ---------------- 报修 ----------------

    @Override
    @Transactional
    public Device reportRepair(Long id, DeviceRepairRequest request) {
        Device device = getDevice(id);

        // 已有未完工的工单就别重复报修，否则同一台设备会挂出多张进行中的单子
        List<DeviceRepair> unfinished = deviceRepairRepository
                .findByDeviceIdAndRepairStatusNot(id, DeviceRepair.STATUS_FINISHED);
        if (!unfinished.isEmpty()) {
            throw new IllegalStateException("该设备已有未完成的维修工单，请先处理");
        }

        // 报修人没填就用当前登录用户兜底
        String reporter = request.getReporter();
        if (reporter == null || reporter.isBlank()) {
            reporter = UserContext.getUsername();
        }

        DeviceRepair repair = new DeviceRepair();
        repair.setDeviceId(device.getId());
        // 存设备名快照，历史工单不受后续改名/删除影响
        repair.setDeviceName(device.getDeviceName());
        repair.setFaultDesc(request.getFaultDesc());
        repair.setRepairStatus(DeviceRepair.STATUS_PENDING);
        repair.setReporter(reporter);
        repair.setReportTime(LocalDateTime.now());
        deviceRepairRepository.save(repair);

        // 建单日志。工单的维修历史从这里开始，后面的状态流转和人工记录继续往上加。
        DeviceRepairLog createdLog = new DeviceRepairLog();
        createdLog.setRepairId(repair.getId());
        createdLog.setLogType(DeviceRepairLog.TYPE_CREATED);
        createdLog.setContent("工单已创建，故障描述：" + request.getFaultDesc());
        createdLog.setOperator(reporter);
        createdLog.setLogTime(repair.getReportTime());
        deviceRepairLogRepository.save(createdLog);

        // 发布事件触发 AI 分析。
        // 这里只是"发个通知"就返回，不直接调 AI —— 直接调会让用户等十几秒，
        // 而且前端 axios 超时是 10 秒，必然报错。
        // 监听器会在**本事务提交之后**才真正启动分析，避免异步线程读不到刚插入的工单。
        eventPublisher.publishEvent(new RepairCreatedEvent(
                repair.getId(), repair.getDeviceName(), repair.getFaultDesc()));

        // 借出中的设备送去维修，顺手把借用信息清掉 ——
        // 否则工单完成后设备会既"在线"又显示着借用人
        device.setBorrower(null);
        device.setBorrowTime(null);
        device.setStatus(Device.STATUS_REPAIRING);
        return deviceRepository.save(device);
    }

    // ---------------- 图表统计 ----------------

    @Override
    public DeviceStatsVO stats() {
        DeviceStatsVO vo = new DeviceStatsVO();
        vo.setTotal(deviceRepository.count());

        // 状态分布：直接取数据库 group by 的结果
        List<ChartItemVO> statusItems = new ArrayList<>();
        for (Object[] row : deviceRepository.countGroupByStatus()) {
            String status = row[0] == null ? "未知" : String.valueOf(row[0]);
            statusItems.add(new ChartItemVO(status, ((Number) row[1]).longValue()));
        }
        vo.setStatusItems(statusItems);

        // 分类统计：group by 出来的是 categoryId，需要换成名称。
        // 分类数量很少，一次查全表在内存里做映射即可，不用 join。
        Map<Long, String> categoryNames = deviceCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCategory::getId, DeviceCategory::getCategoryName));

        List<ChartItemVO> categoryItems = new ArrayList<>();
        for (Object[] row : deviceRepository.countGroupByCategory()) {
            Long cid = row[0] == null ? null : ((Number) row[0]).longValue();
            long count = ((Number) row[1]).longValue();
            String name = cid == null
                    ? "未分类"
                    : categoryNames.getOrDefault(cid, "分类#" + cid);
            categoryItems.add(new ChartItemVO(name, count));
        }
        vo.setCategoryItems(categoryItems);

        return vo;
    }

    // ---------------- 私有辅助 ----------------

    private Device getDevice(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在，id = " + id));
    }

    private void checkAssetCodeUnique(String assetCode, Long selfId) {
        if (assetCode == null || assetCode.isBlank()) {
            return;
        }
        if (selfId == null) {
            if (deviceRepository.existsByAssetCode(assetCode)) {
                throw new IllegalStateException("资产编号已存在：" + assetCode);
            }
        } else {
            // 修改时如果编号没变，不应该和自己撞上
            Device current = getDevice(selfId);
            if (!assetCode.equals(current.getAssetCode())
                    && deviceRepository.existsByAssetCode(assetCode)) {
                throw new IllegalStateException("资产编号已存在：" + assetCode);
            }
        }
    }

    private void checkSerialNumberUnique(String serialNumber, Long selfId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return;
        }
        if (selfId == null) {
            if (deviceRepository.existsBySerialNumber(serialNumber)) {
                throw new IllegalStateException("序列号已存在：" + serialNumber);
            }
        } else {
            Device current = getDevice(selfId);
            if (!serialNumber.equals(current.getSerialNumber())
                    && deviceRepository.existsBySerialNumber(serialNumber)) {
                throw new IllegalStateException("序列号已存在：" + serialNumber);
            }
        }
    }
}
