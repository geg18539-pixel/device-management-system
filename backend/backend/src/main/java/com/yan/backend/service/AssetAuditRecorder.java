package com.yan.backend.service;

import com.yan.backend.common.LoginUser;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.AuditFieldChange;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SysDept;
import com.yan.backend.repository.AssetAuditLogRepository;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.SysDeptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 资产审计日志的记录者。
 *
 * <p><b>用法（两步，中间夹着业务改动）</b>
 * <pre>
 *   var draft = auditRecorder.draftForDevice(device, AssetAuditLog.ACTION_TRANSFER);
 *   // ... 正常的业务改动 ...
 *   auditRecorder.commit(draft, request.getReason());
 * </pre>
 *
 * <p>之所以要"先拍快照、改完再提交"，是因为这几个 service 方法都是
 * **在托管实体上原地就地改**（`existing.setXxx(...)`）。改完之后再去
 * 读"旧值"读到的已经是新值了 —— 那样记出来的审计永远是"无变更"。
 * 草稿里除了旧值快照，还握着一个"重新取一次当前值"的 {@link Supplier}，
 * 两边拿的是**同一个实体实例**，所以提交时读到的一定是改动后的值。
 *
 * <p><b>为什么不用 AOP 切面</b>：切面能知道"调了哪个方法"，但不知道
 * "哪个字段从什么变成了什么"。审计的价值恰恰在后者，所以必须在
 * Service 层显式埋点，让写代码的人明确说出"这里改了哪些字段"。
 * 这和 {@code OperLogRecorder} 用切面的场景不同 —— 那个记的是请求，不是变更。
 *
 * <p><b>写入必须和业务改动在同一个事务里</b>（{@code Propagation.MANDATORY}）。
 * 这一点和登录日志**刚好相反**：登录日志要 REQUIRES_NEW，因为认证失败时会抛异常、
 * 日志会被一起回滚，而失败记录恰恰最有价值；审计日志则必须和变更同生共死 ——
 * 如果变更成功了但审计没写进去，就出现了一条"没人知道是谁改的"数据，
 * 正是审计要防的事。用 MANDATORY 让这条约束由框架强制，而不是靠自觉：
 * 谁要是从一个没有事务的地方调用它，会立刻报错，而不是静默地在自己的事务里写进去。
 */
@Component
public class AssetAuditRecorder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 空值的统一显示。用"空"而不是 null 或 ""，让审计里能明确看出"改成了空" */
    private static final String EMPTY = "空";

    private static final String DEVICE_NAME_KEY = "设备名称";
    private static final String DEVICE_CODE_KEY = "资产编号";
    private static final String PART_NAME_KEY = "配件名称";
    private static final String PART_CODE_KEY = "配件编码";

    private final AssetAuditLogRepository auditLogRepository;
    private final SysDeptRepository sysDeptRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final ObjectMapper objectMapper;

    public AssetAuditRecorder(AssetAuditLogRepository auditLogRepository,
                              SysDeptRepository sysDeptRepository,
                              DeviceCategoryRepository deviceCategoryRepository,
                              ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.sysDeptRepository = sysDeptRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 草稿
    // ============================================================

    /**
     * 一次待提交的变更。持有业务对象在**改动之前**的字段快照。
     *
     * <p>调用方拿到引用、原样传回来即可，不需要（也不应该）读里面的内容。
     */
    public static final class Draft {

        private final String bizType;
        private final Long bizId;
        private final String action;
        private final String nameKey;
        private final String codeKey;
        /** 改动前的「字段标签 → 显示值」 */
        private final Map<String, String> before;
        /** 提交时重新取一次当前值。闭包持有的是同一个实体实例，所以拿到的是改后的值 */
        private final Supplier<Map<String, String>> afterSupplier;
        private String remark;

        private Draft(String bizType, Long bizId, String action, String nameKey, String codeKey,
                      Map<String, String> before, Supplier<Map<String, String>> afterSupplier) {
            this.bizType = bizType;
            this.bizId = bizId;
            this.action = action;
            this.nameKey = nameKey;
            this.codeKey = codeKey;
            this.before = before;
            this.afterSupplier = afterSupplier;
        }

        /** 补一条"为什么改"。调拨原因、报废原因这类信息走这里 */
        public Draft remark(String remark) {
            this.remark = remark;
            return this;
        }
    }

    /** 给设备拍快照，准备记录一次变更 */
    public Draft draftForDevice(Device device, String action) {
        return new Draft(AssetAuditLog.BIZ_DEVICE, device.getId(), action,
                DEVICE_NAME_KEY, DEVICE_CODE_KEY,
                snapshotDevice(device), () -> snapshotDevice(device));
    }

    /** 给配件拍快照，准备记录一次变更 */
    public Draft draftForPart(SparePart part, String action) {
        return new Draft(AssetAuditLog.BIZ_PART, part.getId(), action,
                PART_NAME_KEY, PART_CODE_KEY,
                snapshotPart(part), () -> snapshotPart(part));
    }

    // ============================================================
    // 提交
    // ============================================================

    /** 用草稿里自带的备注提交 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Draft draft) {
        commit(draft, draft.remark);
    }

    /**
     * 比对新旧快照并落库。
     *
     * <p>记录规则：
     * <ul>
     *   <li>有字段变化 → 记一条，changes 里是变化的字段</li>
     *   <li>没有字段变化，但动作是 DELETE → 仍然记（删除本身就是要记的事，
     *       而且它没有"变更字段"可写）</li>
     *   <li>没有字段变化、动作也不是 DELETE → 不记。
     *       用户点开编辑框什么都没改就保存，不该在审计里留一条噪音</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(Draft draft, String remark) {
        Map<String, String> after = draft.afterSupplier.get();
        List<AuditFieldChange> changes = diff(draft.before, after);

        if (changes.isEmpty() && !AssetAuditLog.ACTION_DELETE.equals(draft.action)) {
            return;
        }
        save(draft.bizType, draft.bizId, after.get(draft.nameKey), after.get(draft.codeKey),
                draft.action, changes, remark);
    }

    /**
     * 记录一次"新建"。
     *
     * <p>没有"变更前"，所以单独一个入口 —— 硬塞进 draft 的话，
     * 每次都要传一张空快照，调用方还得解释"为什么 before 是空的"。
     *
     * <p>变更前一律记"空"、逐字段列出初始值，而不是只写一句"新建了设备"：
     * 建档时的字段值本身就是审计要看的东西（比如"入库时保修期填了几年"）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreate(Device device) {
        Map<String, String> after = snapshotDevice(device);
        save(AssetAuditLog.BIZ_DEVICE, device.getId(),
                after.get(DEVICE_NAME_KEY), after.get(DEVICE_CODE_KEY),
                AssetAuditLog.ACTION_CREATE, asCreationChanges(after), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPartCreate(SparePart part) {
        Map<String, String> after = snapshotPart(part);
        save(AssetAuditLog.BIZ_PART, part.getId(),
                after.get(PART_NAME_KEY), after.get(PART_CODE_KEY),
                AssetAuditLog.ACTION_CREATE, asCreationChanges(after), null);
    }

    private List<AuditFieldChange> asCreationChanges(Map<String, String> after) {
        List<AuditFieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            // 建档时就是空的字段不用列出来，否则"新建"的明细里一大半是"空 → 空"
            if (!EMPTY.equals(e.getValue())) {
                changes.add(new AuditFieldChange(e.getKey(), EMPTY, e.getValue()));
            }
        }
        return changes;
    }

    // ============================================================
    // 内部
    // ============================================================

    private void save(String bizType, Long bizId, String bizName, String bizCode,
                      String action, List<AuditFieldChange> changes, String remark) {
        AssetAuditLog log = new AssetAuditLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setBizName(bizName);
        log.setBizCode(bizCode);
        log.setAction(action);
        log.setChangeCount(changes.size());
        log.setChanges(changes.isEmpty() ? null : objectMapper.writeValueAsString(changes));
        log.setRemark(remark);
        log.setAuditTime(LocalDateTime.now());

        LoginUser current = UserContext.get();
        if (current != null) {
            log.setOperator(current.username());
            log.setOperatorName(current.nickname());
        } else {
            // 定时任务、启动期的种子数据这类没有登录上下文的场景
            log.setOperator("系统");
            log.setOperatorName("系统");
        }
        auditLogRepository.save(log);
    }

    private List<AuditFieldChange> diff(Map<String, String> before, Map<String, String> after) {
        List<AuditFieldChange> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : before.entrySet()) {
            String oldValue = e.getValue();
            String newValue = after.get(e.getKey());
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(new AuditFieldChange(e.getKey(), oldValue, newValue));
            }
        }
        return changes;
    }

    // ---------- 快照 ----------

    /**
     * 设备的字段快照。key 直接是**中文标签**，比对时不用再维护一份映射。
     *
     * <p>刻意**不包含** id / createTime / updateTime：它们要么不是"人改的"，
     * 要么是框架自动维护的，记进去只会让每条审计都显示一堆无意义的变动。
     */
    private Map<String, String> snapshotDevice(Device d) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(DEVICE_NAME_KEY, text(d.getDeviceName()));
        m.put("设备类型", text(d.getDeviceType()));
        m.put(DEVICE_CODE_KEY, text(d.getAssetCode()));
        m.put("序列号", text(d.getSerialNumber()));
        m.put("规格型号", text(d.getModel()));
        m.put("生产厂商", text(d.getManufacturer()));
        m.put("所属分类", categoryName(d.getCategoryId()));
        m.put("所属部门", deptName(d.getDeptId()));
        m.put("安装位置", text(d.getLocation()));
        m.put("连通状态", text(d.getStatus()));
        m.put("资产状态", text(d.getLifecycleStatus()));
        m.put("借用人", text(d.getBorrower()));
        m.put("借用时间", dateTime(d.getBorrowTime()));
        m.put("采购日期", date(d.getPurchaseDate()));
        m.put("保修到期", date(d.getWarrantyDate()));
        m.put("报废日期", date(d.getScrapDate()));
        m.put("报废原因", text(d.getScrapReason()));
        m.put("资产说明", text(d.getDescription()));
        return m;
    }

    private Map<String, String> snapshotPart(SparePart p) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(PART_NAME_KEY, text(p.getPartName()));
        m.put(PART_CODE_KEY, text(p.getPartCode()));
        m.put("规格型号", text(p.getModel()));
        m.put("计量单位", text(p.getUnit()));
        m.put("当前库存", number(p.getStockQuantity()));
        m.put("预警阈值", number(p.getWarnThreshold()));
        m.put("单价", amount(p.getUnitPrice()));
        m.put("供应商", text(p.getSupplier()));
        m.put("存放位置", text(p.getLocation()));
        m.put("状态", text(p.getStatus()));
        m.put("备注", text(p.getRemark()));
        return m;
    }

    // ---------- 值格式化 ----------

    private String text(String value) {
        return StringUtils.hasText(value) ? value : EMPTY;
    }

    private String number(Integer value) {
        return value == null ? EMPTY : String.valueOf(value);
    }

    private String date(LocalDate value) {
        return value == null ? EMPTY : value.format(DATE_FMT);
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? EMPTY : value.format(DATETIME_FMT);
    }

    private String amount(BigDecimal value) {
        return value == null ? EMPTY : value.toPlainString();
    }

    /** 分类 id → 名称。分类被删后仍可能有历史引用，查不到就退回原 id */
    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return EMPTY;
        }
        return deviceCategoryRepository.findById(categoryId)
                .map(DeviceCategory::getCategoryName)
                .orElse("分类#" + categoryId);
    }

    /** 部门 id → 名称。null 表示未分配 */
    private String deptName(Long deptId) {
        if (deptId == null) {
            return "未分配";
        }
        return sysDeptRepository.findById(deptId)
                .map(SysDept::getDeptName)
                .orElse("未知部门");
    }
}
