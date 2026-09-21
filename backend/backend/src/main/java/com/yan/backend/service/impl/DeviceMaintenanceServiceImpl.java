package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.MaintenanceExecuteRequest;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceMaintenanceRecord;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceMaintenancePlanRepository;
import com.yan.backend.repository.DeviceMaintenanceRecordRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceMaintenanceService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DeviceMaintenanceServiceImpl implements DeviceMaintenanceService {

    private final DeviceMaintenancePlanRepository planRepository;
    private final DeviceMaintenanceRecordRepository recordRepository;
    private final DeviceRepository deviceRepository;

    public DeviceMaintenanceServiceImpl(DeviceMaintenancePlanRepository planRepository,
                                        DeviceMaintenanceRecordRepository recordRepository,
                                        DeviceRepository deviceRepository) {
        this.planRepository = planRepository;
        this.recordRepository = recordRepository;
        this.deviceRepository = deviceRepository;
    }

    // ============================================================
    // 计划
    // ============================================================

    @Override
    public PageResult<DeviceMaintenancePlan> pagePlans(String status, String keyword,
                                                       int pageNum, int pageSize) {
        Specification<DeviceMaintenancePlan> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("deviceName"), like),
                        cb.like(root.get("planName"), like),
                        cb.like(root.get("maintainer"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 默认排序：**快到期的排前面**。维保页面是拿来看"该干哪些活"的，
        // 按 id 或创建时间排等于让用户自己去找哪些逾期了
        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize,
                Sort.by(Sort.Direction.ASC, "nextMaintenanceDate"));

        return PageResult.of(planRepository.findAll(spec, pageable));
    }

    @Override
    public List<DeviceMaintenancePlan> listPlansByDevice(Long deviceId) {
        return planRepository.findByDeviceId(deviceId).map(List::of).orElse(List.of());
    }

    @Override
    public DeviceMaintenancePlan findPlan(Long id) {
        return getPlan(id);
    }

    @Override
    @Transactional
    public DeviceMaintenancePlan createPlan(DeviceMaintenancePlan plan) {
        plan.setId(null);

        // 一台设备只允许一条计划。允许多条的话，"这台设备下次该什么时候保养"
        // 就没有唯一答案，到期告警也会重复报
        planRepository.findByDeviceId(plan.getDeviceId()).ifPresent(existing -> {
            throw new IllegalStateException("该设备已有维保计划「" + existing.getPlanName()
                    + "」，请直接修改或先删除");
        });

        Device device = requireDevice(plan.getDeviceId());
        plan.setDeviceName(device.getDeviceName());
        refreshNextDate(plan);
        if (!StringUtils.hasText(plan.getStatus())) {
            plan.setStatus(DeviceMaintenancePlan.STATUS_ENABLED);
        }
        return planRepository.save(plan);
    }

    @Override
    @Transactional
    public DeviceMaintenancePlan updatePlan(Long id, DeviceMaintenancePlan plan) {
        DeviceMaintenancePlan existing = getPlan(id);

        existing.setPlanName(plan.getPlanName());
        existing.setCycleDays(plan.getCycleDays());
        existing.setMaintainer(plan.getMaintainer());
        existing.setRemark(plan.getRemark());
        if (StringUtils.hasText(plan.getStatus())) {
            existing.setStatus(plan.getStatus());
        }

        // 设备不允许在计划里改：换了设备等于换了一条计划，
        // 会让已有的维保记录挂到错误的设备上
        if (plan.getLastMaintenanceDate() != null) {
            existing.setLastMaintenanceDate(plan.getLastMaintenanceDate());
        }
        // 下次到期日：前端显式给了就用给的（手工调整），否则按上次保养日 + 周期重算。
        // 重算这一步是必须的 —— 改了周期天数却不让下次日期跟着变，是很容易犯的错
        existing.setNextMaintenanceDate(computeNextDate(
                existing.getLastMaintenanceDate(), existing.getCycleDays(),
                plan.getNextMaintenanceDate()));

        return planRepository.save(existing);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        DeviceMaintenancePlan plan = getPlan(id);

        // 已经产生过维保记录的计划的不能删 —— 记录里的 plan_id 会变成悬空引用，
        // 而且历史保养数据跟着计划一起消失是不可接受的。
        // 不想再保养请把状态改成「停用」
        if (recordRepository.countByPlanId(id) > 0) {
            throw new IllegalStateException("该计划下已有维保记录，不能删除（不想再保养请改为「停用」）");
        }
        planRepository.delete(plan);
    }

    // ============================================================
    // 执行维保
    // ============================================================

    @Override
    @Transactional
    public DeviceMaintenanceRecord execute(Long planId, Long deviceId,
                                           MaintenanceExecuteRequest request) {
        LocalDate date = request.getMaintenanceDate() == null
                ? LocalDate.now() : request.getMaintenanceDate();

        DeviceMaintenancePlan plan = null;
        if (planId != null) {
            plan = getPlan(planId);
            // 以计划上的设备为准，防止前端传的 deviceId 和 planId 对不上
            deviceId = plan.getDeviceId();
        }
        if (deviceId == null) {
            throw new IllegalArgumentException("必须指定设备或维保计划");
        }
        Device device = requireDevice(deviceId);

        DeviceMaintenanceRecord record = new DeviceMaintenanceRecord();
        record.setPlanId(planId);
        record.setDeviceId(device.getId());
        record.setDeviceName(device.getDeviceName());
        record.setMaintenanceDate(date);
        record.setMaintainer(StringUtils.hasText(request.getMaintainer())
                ? request.getMaintainer() : currentOperator());
        record.setContent(request.getContent());
        record.setResult(request.getResult());
        record.setCost(request.getCost());
        record.setRemark(request.getRemark());
        recordRepository.save(record);

        // 推进计划的下次到期日。
        // 用**本次保养日期**（而不是今天）做基准：补录历史保养时，
        // 基准应该是那次保养的时间，否则周期会被算错
        if (plan != null) {
            plan.setLastMaintenanceDate(date);
            plan.setNextMaintenanceDate(date.plusDays(plan.getCycleDays()));
            planRepository.save(plan);
        }

        return record;
    }

    // ============================================================
    // 记录
    // ============================================================

    @Override
    public PageResult<DeviceMaintenanceRecord> pageRecords(Long deviceId, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(pageNum, 1) - 1, pageSize);

        if (deviceId != null) {
            // 设备详情页要的是"按设备查"，用不上分页，这里给它一个足够大的页
            Page<DeviceMaintenanceRecord> page = recordRepository
                    .findByDeviceIdOrderByMaintenanceDateDesc(deviceId, pageable);
            return PageResult.of(page);
        }
        return PageResult.of(recordRepository.findAllByOrderByMaintenanceDateDesc(pageable));
    }

    @Override
    public List<DeviceMaintenanceRecord> listRecordsByDevice(Long deviceId) {
        return recordRepository.findByDeviceIdOrderByMaintenanceDateDesc(deviceId);
    }

    // ============================================================
    // 到期告警
    // ============================================================

    @Override
    public List<DeviceMaintenancePlan> listDue(int warnDays) {
        LocalDate deadline = LocalDate.now().plusDays(warnDays);

        List<DeviceMaintenancePlan> candidates =
                planRepository.findByStatusAndNextMaintenanceDateLessThanEqualOrderByNextMaintenanceDateAsc(
                        DeviceMaintenancePlan.STATUS_ENABLED, deadline);

        // 排除已报废设备：设备都不要了，再提示"该保养了"只会制造噪音。
        // 这里是**一次**查询把所有相关设备捞出来做映射，不是循环里逐条查
        Set<Long> deviceIds = candidates.stream()
                .map(DeviceMaintenancePlan::getDeviceId)
                .collect(Collectors.toSet());

        Map<Long, Device> devices = deviceRepository.findAllById(deviceIds).stream()
                .collect(Collectors.toMap(Device::getId, Function.identity()));

        return candidates.stream()
                .filter(plan -> {
                    Device device = devices.get(plan.getDeviceId());
                    // 设备查不到（被删了）也过滤掉，避免留下指向空设备的告警
                    return device != null
                            && !Device.LIFECYCLE_SCRAPPED.equals(device.getLifecycleStatus());
                })
                .toList();
    }

    @Override
    public long countDue(int warnDays) {
        // 直接复用 listDue 的结果长度，而不是再写一次 count 查询。
        // 到期的计划天然是有限的（正常情况下几十条），而且这样能保证
        // "数量"和"清单"永远一致 —— 两套条件写两遍迟早会对不上
        return listDue(warnDays).size();
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private DeviceMaintenancePlan getPlan(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("维保计划不存在，id = " + id));
    }

    private Device requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("必须指定设备");
        }
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在，id = " + deviceId));
    }

    /** 新建计划时算下次到期日 */
    private void refreshNextDate(DeviceMaintenancePlan plan) {
        plan.setNextMaintenanceDate(computeNextDate(
                plan.getLastMaintenanceDate(), plan.getCycleDays(), plan.getNextMaintenanceDate()));
    }

    /**
     * 算下次到期日。
     *
     * <p>优先用显式给定的值（新建计划时用户可以直接指定首次保养日期），
     * 没给就按"基准日 + 周期天数"推。基准日优先取上次保养日，没有就用今天 ——
     * 一台从没保养过的设备，下次保养就是"从今天起一个周期之后"。
     */
    private LocalDate computeNextDate(LocalDate lastDate, Integer cycleDays, LocalDate explicitNext) {
        if (explicitNext != null) {
            return explicitNext;
        }
        int cycle = (cycleDays == null || cycleDays < 1) ? 1 : cycleDays;
        LocalDate base = lastDate != null ? lastDate : LocalDate.now();
        return base.plusDays(cycle);
    }

    private String currentOperator() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }
}
