package com.yan.backend.service.impl;

import com.yan.backend.common.DictTypes;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.ChartItemVO;
import com.yan.backend.dto.DeviceBorrowRequest;
import com.yan.backend.dto.DeviceLedgerVO;
import com.yan.backend.dto.DeviceQuery;
import com.yan.backend.dto.DeviceRepairRequest;
import com.yan.backend.dto.DeviceScrapRequest;
import com.yan.backend.dto.DeviceStatsVO;
import com.yan.backend.dto.DeviceTransferRequest;
import com.yan.backend.dto.LedgerItemVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceAttachment;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.entity.DeviceTransfer;
import com.yan.backend.entity.SysDept;
import com.yan.backend.event.RepairCreatedEvent;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceAttachmentRepository;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceMaintenancePlanRepository;
import com.yan.backend.repository.DeviceMaintenanceRecordRepository;
import com.yan.backend.repository.DeviceRepairLogRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.DeviceTransferRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.service.AssetAuditRecorder;
import com.yan.backend.service.DeviceService;
import com.yan.backend.service.FileStorageService;
import com.yan.backend.service.SysDictService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DeviceServiceImpl implements DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceServiceImpl.class);

    /** 单次导出最多多少条，防止把整个工作簿建进内存时撑爆堆 */
    private static final int EXPORT_LIMIT = 10_000;

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepairLogRepository deviceRepairLogRepository;
    private final DeviceTransferRepository deviceTransferRepository;
    private final DeviceAttachmentRepository deviceAttachmentRepository;
    private final DeviceMaintenancePlanRepository maintenancePlanRepository;
    private final DeviceMaintenanceRecordRepository maintenanceRecordRepository;
    private final SysDeptRepository sysDeptRepository;
    private final FileStorageService fileStorageService;
    private final SysDictService sysDictService;
    private final ApplicationEventPublisher eventPublisher;
    private final AssetAuditRecorder auditRecorder;

    public DeviceServiceImpl(DeviceRepository deviceRepository,
                             DeviceCategoryRepository deviceCategoryRepository,
                             DeviceRepairRepository deviceRepairRepository,
                             DeviceRepairLogRepository deviceRepairLogRepository,
                             DeviceTransferRepository deviceTransferRepository,
                             DeviceAttachmentRepository deviceAttachmentRepository,
                             DeviceMaintenancePlanRepository maintenancePlanRepository,
                             DeviceMaintenanceRecordRepository maintenanceRecordRepository,
                             SysDeptRepository sysDeptRepository,
                             FileStorageService fileStorageService,
                             SysDictService sysDictService,
                             ApplicationEventPublisher eventPublisher,
                             AssetAuditRecorder auditRecorder) {
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepairLogRepository = deviceRepairLogRepository;
        this.deviceTransferRepository = deviceTransferRepository;
        this.deviceAttachmentRepository = deviceAttachmentRepository;
        this.maintenancePlanRepository = maintenancePlanRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.sysDeptRepository = sysDeptRepository;
        this.fileStorageService = fileStorageService;
        this.sysDictService = sysDictService;
        this.eventPublisher = eventPublisher;
        this.auditRecorder = auditRecorder;
    }

    @Override
    public List<Device> findAll() {
        return deviceRepository.findAll();
    }

    @Override
    public PageResult<Device> page(DeviceQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPageNum(), 1) - 1,
                Math.max(query.getPageSize(), 1),
                Sort.by(Sort.Direction.ASC, "id"));

        return PageResult.of(deviceRepository.findAll(buildSpec(query), pageable));
    }

    @Override
    public List<Device> listForExport(DeviceQuery query) {
        // 导出不分页，但要有个上限：数据涨到几万条时，
        // 一次性把整个工作簿建在内存里会把堆撑爆
        return deviceRepository.findAll(buildSpec(query), Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .limit(EXPORT_LIMIT)
                .toList();
    }

    /**
     * 动态拼查询条件，分页和导出共用。
     *
     * <p>共用很重要：导出必须是"所见即所得"——列表页筛出来什么，
     * 导出的就该是什么。两处各写一份条件，迟早会出现
     * "页面筛出 3 条、导出的却是全部"这种问题。
     */
    private Specification<Device> buildSpec(DeviceQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), query.getCategoryId()));
            }
            if (query.getDeptId() != null) {
                predicates.add(cb.equal(root.get("deptId"), query.getDeptId()));
            }
            if (StringUtils.hasText(query.getStatus())) {
                predicates.add(cb.equal(root.get("status"), query.getStatus().trim()));
            }
            if (StringUtils.hasText(query.getLifecycleStatus())) {
                predicates.add(cb.equal(root.get("lifecycleStatus"),
                        query.getLifecycleStatus().trim()));
            }
            if (query.getWarrantyWithinDays() != null) {
                // "到期日在 N 天以内"。**已经过保的也算在内**（到期日 < 今天 自然 ≤ 截止日），
                // 因为它们比快过保的更该被看见
                predicates.add(cb.isNotNull(root.get("warrantyDate")));
                predicates.add(cb.lessThanOrEqualTo(root.get("warrantyDate"),
                        LocalDate.now().plusDays(query.getWarrantyWithinDays())));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim() + "%";
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
    }

    // ---------------- 设备台账 ----------------

    @Override
    public DeviceLedgerVO ledger() {
        DeviceLedgerVO vo = new DeviceLedgerVO();
        vo.setTotal(deviceRepository.count());

        // 部门名 / 分类名映射。数量都不多，一次查全表在内存里做映射即可，不用 join
        Map<Long, String> deptNames = new LinkedHashMap<>();
        for (SysDept dept : sysDeptRepository.findAllByOrderBySortOrderAscIdAsc()) {
            deptNames.put(dept.getId(), dept.getDeptName());
        }
        Map<Long, String> categoryNames = new LinkedHashMap<>();
        for (DeviceCategory category : deviceCategoryRepository.findAll()) {
            categoryNames.put(category.getId(), category.getCategoryName());
        }

        vo.setDeptItems(pivot(deviceRepository.countGroupByDeptAndLifecycle(), deptNames, "未分配"));
        vo.setCategoryItems(pivot(deviceRepository.countGroupByCategoryAndLifecycle(),
                categoryNames, "未分类"));

        // 总计直接由按部门的分组累加得出。
        // 不另外查一次 count，是为了保证"总计"和下面各行的和永远对得上 ——
        // 分开查就可能出现两边差一两条的情况
        long normal = 0;
        long repairing = 0;
        long scrapped = 0;
        long disabled = 0;
        for (LedgerItemVO item : vo.getDeptItems()) {
            normal += item.getNormal();
            repairing += item.getRepairing();
            scrapped += item.getScrapped();
            disabled += item.getDisabled();
        }
        vo.setNormal(normal);
        vo.setRepairing(repairing);
        vo.setScrapped(scrapped);
        vo.setDisabled(disabled);

        return vo;
    }

    /**
     * 把「分组 id × 生命周期状态 × 数量」的交叉查询结果透视成
     * 「一行一个分组、四种状态各一列」。
     *
     * @param rows      每项是 [groupId, lifecycleStatus, count]
     * @param nameMap   id → 名称
     * @param nullLabel 分组 id 为 null 时显示的名字（未分配 / 未分类）
     */
    private List<LedgerItemVO> pivot(List<Object[]> rows, Map<Long, String> nameMap,
                                     String nullLabel) {
        // LinkedHashMap 允许一个 null 键，正好用来表示"未分配/未分类"这一组
        Map<Long, LedgerItemVO> byGroup = new LinkedHashMap<>();

        for (Object[] row : rows) {
            Long groupId = row[0] == null ? null : ((Number) row[0]).longValue();
            String lifecycle = row[1] == null
                    ? Device.LIFECYCLE_NORMAL : String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();

            LedgerItemVO item = byGroup.computeIfAbsent(groupId, key -> {
                String name = key == null ? nullLabel : nameMap.getOrDefault(key, "未知#" + key);
                return new LedgerItemVO(name);
            });

            item.setTotal(item.getTotal() + count);
            switch (lifecycle) {
                case Device.LIFECYCLE_NORMAL -> item.setNormal(item.getNormal() + count);
                case Device.LIFECYCLE_REPAIR -> item.setRepairing(item.getRepairing() + count);
                case Device.LIFECYCLE_SCRAPPED -> item.setScrapped(item.getScrapped() + count);
                case Device.LIFECYCLE_DISABLED -> item.setDisabled(item.getDisabled() + count);
                // 出现未知状态值时只计入 total。真出现的话说明数据被手工改过，
                // 这时"合计对不上分类之和"反而是有用的信号，不该悄悄抹平
                default -> log.warn("台账遇到未知的生命周期状态：{}，只计入合计", lifecycle);
            }
        }
        return new ArrayList<>(byGroup.values());
    }

    @Override
    public Device findById(Long id) {
        return getDevice(id);
    }

    @Override
    @Transactional
    public Device save(Device device) {
        device.setId(null);
        // 请求体里显式带 lifecycleStatus: null 时会覆盖掉实体上的字段默认值，
        // 那样新设备在库里就是"无生命周期状态"，看板上会凭空多出一个"未知"分类。
        // 这里兜一下底，统一落到"正常"。
        if (device.getLifecycleStatus() == null || device.getLifecycleStatus().isBlank()) {
            device.setLifecycleStatus(Device.LIFECYCLE_NORMAL);
        }
        checkAssetCodeUnique(device.getAssetCode(), null);
        checkSerialNumberUnique(device.getSerialNumber(), null);
        Device saved = deviceRepository.save(device);
        // 建档也要进审计：初始字段值本身就是审计要看的东西
        // （比如"入库时保修期填了几年"），只记一句"新建了设备"会丢掉它
        auditRecorder.recordCreate(saved);
        return saved;
    }

    @Override
    @Transactional
    public Device update(Long id, Device device) {
        Device existing = getDevice(id);

        // ⚠️ 快照必须在改动**之前**拍。这个方法是就地改托管实体
        // （existing.setXxx），改完再读"旧值"读到的已经是新值了
        var auditDraft = auditRecorder.draftForDevice(existing, AssetAuditLog.ACTION_UPDATE);

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

        // ★ 下面四个字段必须显式赋值。
        // 这里的 update 是"逐字段搬运"而不是整体替换（现有的设备对象是从库里查出来的，
        // 直接覆盖会把 createTime 之类的托管字段也冲掉），所以**凡是实体上有、
        // 表单能改的字段，这里漏一个就等于那个字段永远改不动** ——
        // 不报错、也不回滚，只是用户改完发现没生效。
        existing.setModel(device.getModel());
        existing.setManufacturer(device.getManufacturer());

        // ★ deptId 在这里**刻意不赋值**。
        // 设备归属部门的变更必须走 transfer()，那样才会留下调拨记录。
        // 如果编辑表单也能直接改 deptId，调拨历史就会漏掉这些变更 ——
        // 审计链一断，"这台设备什么时候到的一号车间"就说不清了。
        //
        // 新增设备时（save）仍然可以指定初始部门，那是"建档"而不是"调拨"。

        // 生命周期状态允许为空：老数据（加这一列之前建的）会是 null，
        // 而表单没提供这一项时也不该被清空。为空时按"正常"处理。
        String lifecycle = device.getLifecycleStatus();
        existing.setLifecycleStatus(
                (lifecycle == null || lifecycle.isBlank()) ? Device.LIFECYCLE_NORMAL : lifecycle);

        // 借用信息不在这里改 —— 那是 borrow / giveBack 的职责，
        // 编辑表单不该能把"谁借着"改掉
        Device saved = deviceRepository.save(existing);
        // 什么都没改就点保存时，commit 内部会跳过，不留噪音记录
        auditRecorder.commit(auditDraft);
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Device device = getDevice(id);

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_DELETE);

        if (device.getBorrower() != null && !device.getBorrower().isBlank()) {
            throw new IllegalStateException("设备已被「" + device.getBorrower() + "」借出，不能删除");
        }
        // 有维修历史就不给删。工单里存的是 device_id，
        // 设备删了工单就变成指向不存在记录的悬空引用
        if (deviceRepairRepository.existsByDeviceId(id)) {
            throw new IllegalStateException("该设备存在维修工单记录，不能删除");
        }
        // 下面这些同样属于**审计资料**，删掉设备会让它们变成悬空引用，
        // 而且历史本来就该留痕 —— 所以一律拦住，不级联删除
        if (deviceTransferRepository.existsByDeviceId(id)) {
            throw new IllegalStateException("该设备存在调拨记录，不能删除（如需淘汰请改为报废）");
        }
        if (maintenanceRecordRepository.existsByDeviceId(id)) {
            throw new IllegalStateException("该设备存在维保记录，不能删除（如需淘汰请改为报废）");
        }

        // 附件和维保计划是**从属于设备的配置/资料**，设备没了它们就没有意义，
        // 所以这两类是级联清理（附件还要把磁盘文件一并删掉，避免留孤儿文件）
        List<DeviceAttachment> attachments = deviceAttachmentRepository.findByDeviceId(id);
        for (DeviceAttachment attachment : attachments) {
            fileStorageService.delete(attachment.getStoredName());
        }
        deviceAttachmentRepository.deleteAll(attachments);

        maintenancePlanRepository.findByDeviceId(id)
                .ifPresent(maintenancePlanRepository::delete);

        deviceRepository.deleteById(id);
        // 删除没有"字段变化"，但 commit 对 DELETE 动作会强制记一条 ——
        // "谁删了哪台设备"恰恰是审计最该留下的东西。
        //
        // 注意这里是**先删业务数据、后写审计**，两者在同一个事务里，
        // 所以要么都成功、要么都回滚，不会出现"设备没了但没有记录"的中间态。
        //
        // 另外：审计记录**不阻止**设备被删除（对比调拨/维保记录会阻止）。
        // 因为每台设备都被编辑过，用审计表拦删除等于让所有设备都删不掉。
        // 审计里存了名称和编号快照，设备删了之后历史仍然可读
        auditRecorder.commit(auditDraft);
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

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_BORROW);

        device.setBorrower(request.getBorrower());
        device.setBorrowTime(LocalDateTime.now());
        device.setStatus(Device.STATUS_IN_USE);
        Device saved = deviceRepository.save(device);
        auditRecorder.commit(auditDraft);
        return saved;
    }

    @Override
    @Transactional
    public Device giveBack(Long id) {
        Device device = getDevice(id);

        if (device.getBorrower() == null || device.getBorrower().isBlank()) {
            throw new IllegalStateException("该设备当前没有被借出");
        }

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_RETURN);

        device.setBorrower(null);
        device.setBorrowTime(null);
        device.setStatus(Device.STATUS_ONLINE);
        Device saved = deviceRepository.save(device);
        auditRecorder.commit(auditDraft);
        return saved;
    }

    // ---------------- 报修 ----------------

    @Override
    @Transactional
    public Device reportRepair(Long id, DeviceRepairRequest request) {
        Device device = getDevice(id);

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_REPAIR);

        // 已有未完工的工单就别重复报修，否则同一台设备会挂出多张进行中的单子。
        // 判断口径用 DeviceRepair.isTerminal（已完成 / 已关闭都算终态）——
        // 直接比较"不等于已完成"的话，已关闭的工单会被误判成"还在处理中"
        if (hasOpenRepair(id)) {
            throw new IllegalStateException("该设备已有未完成的维修工单，请先处理");
        }

        // 报修人没填就用当前登录用户兜底
        String reporter = request.getReporter();
        if (reporter == null || reporter.isBlank()) {
            reporter = UserContext.getUsername();
        }

        // 故障类型如果填了，要确认它是「故障类型」字典里的合法值。
        // 字典没配（或读不到）时放行 —— 一个可选分类字段不该有能力
        // 让报修整体不可用（见 SysDictService.isValidValue 的说明）
        String faultType = request.getFaultType();
        if (StringUtils.hasText(faultType)) {
            faultType = faultType.trim();
            if (!sysDictService.isValidValue(DictTypes.FAULT_TYPE, faultType)) {
                throw new IllegalArgumentException(
                        "故障类型「" + faultType + "」不是有效的字典值，请刷新页面后重选");
            }
        } else {
            faultType = null;
        }

        DeviceRepair repair = new DeviceRepair();
        repair.setDeviceId(device.getId());
        // 存设备名快照，历史工单不受后续改名/删除影响
        repair.setDeviceName(device.getDeviceName());
        repair.setFaultDesc(request.getFaultDesc());
        repair.setFaultType(faultType);
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
        Device saved = deviceRepository.save(device);
        // 备注写故障描述：审计要能回答"当时是因为什么报修的"，
        // 光看"连通状态 在线 → 维修中"是看不出来的
        auditRecorder.commit(auditDraft, "故障描述：" + request.getFaultDesc());
        return saved;
    }

    // ---------------- 调拨 / 报废 ----------------

    @Override
    public List<DeviceTransfer> findTransfers(Long deviceId) {
        return deviceTransferRepository.findByDeviceIdOrderByTransferTimeDesc(deviceId);
    }

    @Override
    @Transactional
    public Device transfer(Long id, DeviceTransferRequest request) {
        Device device = getDevice(id);

        if (Device.LIFECYCLE_SCRAPPED.equals(device.getLifecycleStatus())) {
            throw new IllegalStateException("设备已报废，不能再调拨");
        }
        // 调到同一个部门是个无意义的操作，而且会在历史里留下一条"从A调到A"的噪音记录
        if (Objects.equals(device.getDeptId(), request.getToDeptId())) {
            throw new IllegalStateException("设备当前已在目标部门，无需调拨");
        }
        if (request.getToDeptId() != null
                && !sysDeptRepository.existsById(request.getToDeptId())) {
            throw new IllegalArgumentException("目标部门不存在");
        }

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_TRANSFER);

        // 先写调拨记录，再改设备归属。
        // 两件事在同一个事务里，不会出现"改了部门但没记录"的中间态
        DeviceTransfer transfer = new DeviceTransfer();
        transfer.setDeviceId(device.getId());
        transfer.setDeviceName(device.getDeviceName());
        transfer.setFromDeptId(device.getDeptId());
        transfer.setFromDeptName(deptNameOf(device.getDeptId()));
        transfer.setToDeptId(request.getToDeptId());
        transfer.setToDeptName(deptNameOf(request.getToDeptId()));
        transfer.setReason(request.getReason());
        transfer.setOperator(currentOperator());
        transfer.setTransferTime(LocalDateTime.now());
        deviceTransferRepository.save(transfer);

        device.setDeptId(request.getToDeptId());
        Device saved = deviceRepository.save(device);
        // 调拨原因是审计里"为什么改"的那部分。它不在 Device 的任何字段上，
        // 只存在于请求里，不显式带过来就会丢掉
        auditRecorder.commit(auditDraft, request.getReason());
        return saved;
    }

    @Override
    @Transactional
    public Device scrap(Long id, DeviceScrapRequest request) {
        Device device = getDevice(id);

        if (Device.LIFECYCLE_SCRAPPED.equals(device.getLifecycleStatus())) {
            throw new IllegalStateException("设备已经是报废状态");
        }
        // 报废一台正被人借走的设备，会让记录变成"报废 + 还显示着借用人"这种自相矛盾的状态
        if (device.getBorrower() != null && !device.getBorrower().isBlank()) {
            throw new IllegalStateException(
                    "设备已被「" + device.getBorrower() + "」借出，请先归还再报废");
        }
        // 还有没走完的维修工单时也拦住。业务上"修不好所以报废"是合理的，
        // 但正确的顺序是先把工单完结（写清楚维修结果），再报废 ——
        // 否则那张工单会永远挂在"维修中"，而设备已经报废了
        if (hasOpenRepair(id)) {
            throw new IllegalStateException("该设备还有未完成的维修工单，请先完结工单再报废");
        }

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_SCRAP);

        device.setLifecycleStatus(Device.LIFECYCLE_SCRAPPED);
        device.setScrapDate(request.getScrapDate() == null ? LocalDate.now() : request.getScrapDate());
        device.setScrapReason(request.getReason());
        device.setScrapOperator(currentOperator());
        Device saved = deviceRepository.save(device);
        auditRecorder.commit(auditDraft, request.getReason());
        return saved;
    }

    @Override
    @Transactional
    public Device restore(Long id) {
        Device device = getDevice(id);

        // 只允许"报废 → 正常"这一步。放开成任意状态互转的话，
        // 这个方法会变成一个绕过状态机的后门
        if (!Device.LIFECYCLE_SCRAPPED.equals(device.getLifecycleStatus())) {
            throw new IllegalStateException("只有已报废的设备才能恢复");
        }

        var auditDraft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_RESTORE);

        device.setLifecycleStatus(Device.LIFECYCLE_NORMAL);
        device.setScrapDate(null);
        device.setScrapReason(null);
        device.setScrapOperator(null);
        Device saved = deviceRepository.save(device);
        // 恢复会把报废日期/原因清空，审计里能看到这一串字段从有到无
        auditRecorder.commit(auditDraft);
        return saved;
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

    /**
     * 部门 id → 名称。null 表示未分配。
     *
     * <p>部门被删掉后仍可能有历史记录引用它的 id，所以查不到时给"未知部门"
     * 而不是抛异常 —— 历史记录的可读性不该因为部门被删就整页报错。
     */
    private String deptNameOf(Long deptId) {
        if (deptId == null) {
            return "未分配";
        }
        return sysDeptRepository.findById(deptId)
                .map(SysDept::getDeptName)
                .orElse("未知部门");
    }

    private String currentOperator() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }

    /**
     * 该设备是否还有未走到终态的维修工单。
     *
     * <p>终态 = 已完成 或 已关闭（判断逻辑收在 {@link DeviceRepair#isTerminal} 里）。
     * 用**排除终态**而不是"等于某几个进行中状态"，这样以后状态流转再细化出
     * 更多中间态时，这里不用跟着改。
     */
    private boolean hasOpenRepair(Long deviceId) {
        return deviceRepairRepository.findByDeviceIdOrderByReportTimeDesc(deviceId).stream()
                .anyMatch(repair -> !DeviceRepair.isTerminal(repair.getRepairStatus()));
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
