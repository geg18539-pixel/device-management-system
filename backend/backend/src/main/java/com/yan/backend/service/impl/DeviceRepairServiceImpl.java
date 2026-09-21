package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.ChartItemVO;
import com.yan.backend.dto.DeviceRepairFinishRequest;
import com.yan.backend.dto.DeviceRepairQuery;
import com.yan.backend.dto.DeviceRepairStatsVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.DeviceRepairLog;
import com.yan.backend.entity.RepairAttachment;
import com.yan.backend.entity.SysMessage;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairLogRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.RepairAttachmentRepository;
import com.yan.backend.service.AiFaultAnalysisService;
import com.yan.backend.service.AssetAuditRecorder;
import com.yan.backend.service.DeviceRepairService;
import com.yan.backend.service.FileStorageService;
import com.yan.backend.service.SysMessageService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DeviceRepairServiceImpl implements DeviceRepairService {

    /** 单次导出上限，防止把整个工作簿建进内存时撑爆堆 */
    private static final int EXPORT_LIMIT = 10_000;

    /** 平均维修时长的统计窗口（天）。不限窗口的话工单表积累几年后这个查询会越来越慢 */
    private static final int DURATION_WINDOW_DAYS = 90;

    private final DeviceRepairRepository deviceRepairRepository;
    private final DeviceRepairLogRepository deviceRepairLogRepository;
    private final RepairAttachmentRepository repairAttachmentRepository;
    private final DeviceRepository deviceRepository;
    private final FileStorageService fileStorageService;
    private final AiFaultAnalysisService aiFaultAnalysisService;
    private final SysMessageService messageService;
    private final AssetAuditRecorder auditRecorder;

    public DeviceRepairServiceImpl(DeviceRepairRepository deviceRepairRepository,
                                   DeviceRepairLogRepository deviceRepairLogRepository,
                                   RepairAttachmentRepository repairAttachmentRepository,
                                   DeviceRepository deviceRepository,
                                   FileStorageService fileStorageService,
                                   AiFaultAnalysisService aiFaultAnalysisService,
                                   SysMessageService messageService,
                                   AssetAuditRecorder auditRecorder) {
        this.deviceRepairRepository = deviceRepairRepository;
        this.deviceRepairLogRepository = deviceRepairLogRepository;
        this.repairAttachmentRepository = repairAttachmentRepository;
        this.deviceRepository = deviceRepository;
        this.fileStorageService = fileStorageService;
        this.aiFaultAnalysisService = aiFaultAnalysisService;
        this.messageService = messageService;
        this.auditRecorder = auditRecorder;
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    public PageResult<DeviceRepair> page(DeviceRepairQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPageNum(), 1) - 1,
                Math.max(query.getPageSize(), 1),
                Sort.by(Sort.Direction.DESC, "reportTime"));

        return PageResult.of(deviceRepairRepository.findAll(buildSpec(query), pageable));
    }

    @Override
    public List<DeviceRepair> listForExport(DeviceRepairQuery query) {
        return deviceRepairRepository
                .findAll(buildSpec(query), Sort.by(Sort.Direction.DESC, "reportTime"))
                .stream()
                .limit(EXPORT_LIMIT)
                .toList();
    }

    /**
     * 动态拼查询条件。分页和导出共用 —— 共用才能保证"页面筛出什么，导出的就是什么"。
     */
    private Specification<DeviceRepair> buildSpec(DeviceRepairQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.getRepairStatus())) {
                // ★ 用 expandStatusFilter 而不是等值匹配：
                // 库里改造前建的工单存的还是「待维修」，只匹配「待受理」的话，
                // 用户一点"待受理"会发现历史工单全不见了 —— 看起来像数据丢了
                predicates.add(root.get("repairStatus")
                        .in(DeviceRepair.expandStatusFilter(query.getRepairStatus().trim())));
            }

            if (query.getDeviceId() != null) {
                predicates.add(cb.equal(root.get("deviceId"), query.getDeviceId()));
            }

            if (StringUtils.hasText(query.getRepairer())) {
                predicates.add(cb.like(root.get("repairer"),
                        "%" + query.getRepairer().trim() + "%"));
            }

            if (StringUtils.hasText(query.getFaultType())) {
                // 等值匹配：故障类型存的是字典项的值（稳定编码），
                // 不像状态那样有历史值要兼容
                predicates.add(cb.equal(root.get("faultType"), query.getFaultType().trim()));
            }

            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("deviceName"), like),
                        cb.like(root.get("faultDesc"), like),
                        cb.like(root.get("reporter"), like)));
            }

            if (Boolean.TRUE.equals(query.getPendingOnly())) {
                // 待处理 = 不在终态里。用"排除终态"而不是列举进行中状态，
                // 以后加中间态（已派单、等配件…）这里不用改
                predicates.add(cb.and(
                        cb.notEqual(root.get("repairStatus"), DeviceRepair.STATUS_FINISHED),
                        cb.notEqual(root.get("repairStatus"), DeviceRepair.STATUS_CLOSED)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    public DeviceRepair findById(Long id) {
        return deviceRepairRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("维修工单不存在，id = " + id));
    }

    // ============================================================
    // 状态流转
    // ============================================================

    @Override
    @Transactional
    public DeviceRepair accept(Long id) {
        DeviceRepair repair = findById(id);
        String current = DeviceRepair.normalizeStatus(repair.getRepairStatus());

        if (DeviceRepair.STATUS_REPAIRING.equals(current)) {
            throw new IllegalStateException("工单已经是「维修中」了");
        }
        if (DeviceRepair.isTerminal(current)) {
            throw new IllegalStateException("工单已「" + current + "」，不能再受理");
        }

        LocalDateTime now = LocalDateTime.now();
        // 这里会把旧值「待维修」一并覆盖成「维修中」——状态流转顺带把历史值归一了
        repair.setRepairStatus(DeviceRepair.STATUS_REPAIRING);
        repair.setAcceptTime(now);
        // 还没指派过维修人时，受理人默认就是维修人。
        // 否则工单会停在"维修中但没人负责"的状态，列表上看着像漏了数据
        if (!StringUtils.hasText(repair.getRepairer())) {
            repair.setRepairer(currentOperator());
        }
        deviceRepairRepository.save(repair);

        writeLog(repair.getId(), DeviceRepairLog.TYPE_STATUS,
                "工单已受理，开始维修（维修人：" + repair.getRepairer() + "）",
                currentOperator(), now);
        return repair;
    }

    @Override
    @Transactional
    public DeviceRepair assign(Long id, String repairer) {
        DeviceRepair repair = findById(id);

        if (DeviceRepair.isTerminal(repair.getRepairStatus())) {
            throw new IllegalStateException(
                    "工单已「" + DeviceRepair.normalizeStatus(repair.getRepairStatus())
                            + "」，不能指派维修人员");
        }
        if (!StringUtils.hasText(repairer)) {
            throw new IllegalArgumentException("维修人员不能为空");
        }

        String previous = repair.getRepairer();
        LocalDateTime now = LocalDateTime.now();
        String newRepairer = repairer.trim();
        repair.setRepairer(newRepairer);
        repair.setAssignTime(now);
        deviceRepairRepository.save(repair);

        // 改派也要留痕：谁把工单从谁手上转给了谁，是追责和统计工作量的依据
        String content = StringUtils.hasText(previous)
                ? "改派维修人员：" + previous + " → " + newRepairer
                : "指派维修人员：" + newRepairer;
        writeLog(repair.getId(), DeviceRepairLog.TYPE_STATUS, content, currentOperator(), now);

        // 给被指派人发站内通知。
        // 只在**维修人真的变了**的时候发 —— 同一张单重复指派给同一个人
        // 还弹一条通知，只会让人以为是新任务
        if (!newRepairer.equals(previous)) {
            notifyAssignee(repair, newRepairer);
        }
        return repair;
    }

    /**
     * 通知被指派的维修人。
     *
     * <p>按**用户名**解析账号（下拉里选的就是用户名）。手填的姓名
     * （比如外部临时维修工）解析不到账号，{@code sendToUsername} 会打一条 WARN
     * 并返回 false —— 通知发不出去是预期内的，但不能静默。
     */
    private void notifyAssignee(DeviceRepair repair, String repairer) {
        String title = "有新工单指派给你";
        StringBuilder content = new StringBuilder()
                .append("工单 #").append(repair.getId());
        if (StringUtils.hasText(repair.getDeviceName())) {
            content.append("｜设备：").append(repair.getDeviceName());
        }
        if (StringUtils.hasText(repair.getFaultDesc())) {
            content.append("｜故障：").append(repair.getFaultDesc());
        }
        content.append("\n请及时受理并处理。");

        messageService.sendToUsername(
                repairer,
                SysMessage.TYPE_REPAIR_ASSIGN,
                title,
                content.toString(),
                SysMessage.LEVEL_NORMAL,
                SysMessage.BIZ_REPAIR,
                repair.getId(),
                // 指派是可以重复发生的独立事件，不做幂等去重
                null);
    }

    @Override
    @Transactional
    public DeviceRepair finish(Long id, DeviceRepairFinishRequest request) {
        DeviceRepair repair = findById(id);
        String current = DeviceRepair.normalizeStatus(repair.getRepairStatus());

        if (DeviceRepair.isTerminal(current)) {
            throw new IllegalStateException("工单已「" + current + "」，不能重复完工");
        }
        // ★ 严格状态机：必须先受理才能完工。
        // 允许跳过受理的话，"受理时间"会有大量缺口，
        // "报修后多久有人接单"这个指标就彻底失真了 —— 而那正是区分
        // 待受理/维修中两个状态的全部意义
        if (DeviceRepair.isPending(current)) {
            throw new IllegalStateException("工单还没受理，请先点「受理」再完工");
        }
        if (!DeviceRepair.STATUS_REPAIRING.equals(current)) {
            throw new IllegalStateException("工单当前是「" + current + "」，不能完工");
        }

        LocalDateTime now = LocalDateTime.now();
        repair.setRepairStatus(DeviceRepair.STATUS_FINISHED);
        // 维修人兜底顺序：请求里带的 → 工单上已指派的 → 当前登录用户
        repair.setRepairer(resolveRepairer(request.getRepairer(), repair.getRepairer()));
        repair.setRepairResult(request.getRepairResult());
        repair.setCost(request.getCost());
        repair.setFinishTime(now);
        if (request.getRemark() != null) {
            repair.setRemark(request.getRemark());
        }
        deviceRepairRepository.save(repair);

        StringBuilder content = new StringBuilder("工单已完成，维修人：").append(repair.getRepairer());
        if (request.getCost() != null) {
            content.append("，费用：").append(request.getCost());
        }
        content.append("；维修结果：").append(request.getRepairResult());
        writeLog(repair.getId(), DeviceRepairLog.TYPE_STATUS, content.toString(),
                repair.getRepairer(), now);

        restoreDeviceIfRepairing(repair.getDeviceId());
        return repair;
    }

    @Override
    @Transactional
    public DeviceRepair close(Long id, String reason) {
        DeviceRepair repair = findById(id);
        String current = DeviceRepair.normalizeStatus(repair.getRepairStatus());

        if (DeviceRepair.STATUS_CLOSED.equals(current)) {
            throw new IllegalStateException("工单已经是「已关闭」");
        }
        if (DeviceRepair.STATUS_REPAIRING.equals(current)) {
            // 维修中直接关闭 → 设备永远停在"维修中"，而工单已经关掉了，
            // 现场没有可跟进的单据，这类"孤儿设备"事后极难排查
            throw new IllegalStateException("工单正在维修中，请先完工再关闭（或先完工说明处理结果）");
        }

        LocalDateTime now = LocalDateTime.now();
        repair.setRepairStatus(DeviceRepair.STATUS_CLOSED);
        repair.setCloseTime(now);
        repair.setCloseReason(reason);
        deviceRepairRepository.save(repair);

        String stage = DeviceRepair.isPending(current) ? "受理前作废" : "维修完成后归档";
        writeLog(repair.getId(), DeviceRepairLog.TYPE_STATUS,
                "工单已关闭（" + stage + "），原因：" + reason, currentOperator(), now);

        // ★ 从「待受理」直接作废时，设备在报修那一刻就被改成了"维修中"，
        // 这里必须改回"在线"，否则设备就永远卡在维修中了。
        // 从「已完成」关闭时设备早就改回去了，这个调用是幂等的空操作
        restoreDeviceIfRepairing(repair.getDeviceId());
        return repair;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DeviceRepair repair = findById(id);

        // 未到终态的工单不允许删，否则设备会永远停在"维修中"却找不到对应工单
        if (!DeviceRepair.isTerminal(repair.getRepairStatus())) {
            throw new IllegalStateException("工单尚未完成，不能删除");
        }

        // 日志跟着工单一起删，避免留下指向不存在工单的悬空记录
        deviceRepairLogRepository.deleteByRepairId(id);

        // 维修照片同样跟着工单走：工单都不在了，照片作为"这张单的证据"也就失去意义。
        // 但**磁盘文件要一并清掉**，否则上传目录会越积越多孤儿文件
        List<RepairAttachment> attachments = repairAttachmentRepository.findByRepairId(id);
        for (RepairAttachment attachment : attachments) {
            fileStorageService.delete(attachment.getStoredName());
        }
        repairAttachmentRepository.deleteAll(attachments);

        deviceRepairRepository.deleteById(id);
    }

    // ============================================================
    // 统计
    // ============================================================

    @Override
    public DeviceRepairStatsVO stats() {
        DeviceRepairStatsVO vo = new DeviceRepairStatsVO();
        vo.setTotal(deviceRepairRepository.count());
        vo.setPendingCount(deviceRepairRepository.countPending(
                DeviceRepair.STATUS_FINISHED, DeviceRepair.STATUS_CLOSED));

        // 状态分布。旧值「待维修」要归并到「待受理」，
        // 否则图上会平白多出一个历史分类，而"待受理"的数字被拆成两半
        Map<String, Long> merged = new LinkedHashMap<>();
        for (Object[] row : deviceRepairRepository.countGroupByRepairStatus()) {
            String raw = row[0] == null ? null : String.valueOf(row[0]);
            String name = raw == null ? "未知" : DeviceRepair.normalizeStatus(raw);
            merged.merge(name, ((Number) row[1]).longValue(), Long::sum);
        }
        vo.setStatusItems(merged.entrySet().stream()
                .map(e -> new ChartItemVO(e.getKey(), e.getValue()))
                .toList());

        vo.setPending(merged.getOrDefault(DeviceRepair.STATUS_PENDING, 0L));
        vo.setRepairing(merged.getOrDefault(DeviceRepair.STATUS_REPAIRING, 0L));
        vo.setFinished(merged.getOrDefault(DeviceRepair.STATUS_FINISHED, 0L));
        vo.setClosed(merged.getOrDefault(DeviceRepair.STATUS_CLOSED, 0L));

        long total = vo.getTotal();
        vo.setCompletionRate(total == 0
                ? 0.0
                : Math.round((vo.getFinished() + vo.getClosed()) * 1000.0 / total) / 10.0);

        vo.setThisMonthCount(deviceRepairRepository.countByReportTimeGreaterThanEqual(
                LocalDate.now().withDayOfMonth(1).atStartOfDay()));

        buildAvgDuration(vo);
        return vo;
    }

    /**
     * 平均维修时长 = 「受理 → 完工」的平均耗时。
     *
     * <p>刻意**不用**「报修 → 完工」：那里面混着"等有人接单"的时间，
     * 那是响应速度问题，不该算进维修效率。两个指标要分开看才有意义。
     */
    private void buildAvgDuration(DeviceRepairStatsVO vo) {
        List<Object[]> rows = deviceRepairRepository.findDurationsSince(
                LocalDateTime.now().minusDays(DURATION_WINDOW_DAYS));

        double totalHours = 0;
        int samples = 0;
        for (Object[] row : rows) {
            LocalDateTime accept = (LocalDateTime) row[0];
            LocalDateTime finish = (LocalDateTime) row[1];
            if (accept == null || finish == null) {
                continue;
            }
            long minutes = Duration.between(accept, finish).toMinutes();
            if (minutes < 0) {
                // 受理时间晚于完工时间，属于被手工改坏的数据，不计入平均
                continue;
            }
            totalHours += minutes / 60.0;
            samples++;
        }

        // 样本为 0 时保持 null，前端显示「—」。
        // 如果返回 0.0，页面上会显示"平均 0 小时"，看着像瞬修，是误导
        if (samples > 0) {
            vo.setAvgRepairHours(Math.round(totalHours / samples * 10) / 10.0);
            vo.setAvgSampleSize(samples);
        }
    }

    // ============================================================
    // 维修日志
    // ============================================================

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

    // ============================================================
    // AI 分析
    // ============================================================

    @Override
    public void reanalyze(Long repairId) {
        findById(repairId);
        // 跨 bean 调用，@Async 才能真正生效（同类自调用不走代理）
        aiFaultAnalysisService.analyzeAsync(repairId);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

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

    /**
     * 按优先级挑一个维修人：请求里的 → 工单上已指派的 → 当前登录用户。
     */
    private String resolveRepairer(String fromRequest, String assigned) {
        if (StringUtils.hasText(fromRequest)) {
            return fromRequest.trim();
        }
        if (StringUtils.hasText(assigned)) {
            return assigned;
        }
        return currentOperator();
    }

    private String currentOperator() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }

    /**
     * 如果设备当前停在"维修中"，改回"在线"。
     *
     * <p>只在这个状态下才改 —— 否则会覆盖用户后来手工改过的状态
     * （比如设备在这期间已经报废了，不该被工单完工改回"在线"）。
     */
    private void restoreDeviceIfRepairing(Long deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            if (Device.STATUS_REPAIRING.equals(device.getStatus())) {
                // 这里改的是**设备**，所以记的是设备的审计，动作归为"编辑"。
                // 用备注把因果写清楚 —— 单看「连通状态 维修中 → 在线」，
                // 读审计的人不知道是谁、因为什么把它改回来的
                var auditDraft = auditRecorder.draftForDevice(
                        device, AssetAuditLog.ACTION_UPDATE);
                device.setStatus(Device.STATUS_ONLINE);
                deviceRepository.save(device);
                auditRecorder.commit(auditDraft, "维修工单完结，设备恢复在线");
            }
        });
    }
}
