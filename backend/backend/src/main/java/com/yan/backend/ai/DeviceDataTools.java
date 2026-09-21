package com.yan.backend.ai;

import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 让 AI 能看到数据库里的真实数据。
 *
 * <p>两条路子配合使用：
 * <ol>
 *   <li>{@link #buildSnapshot()} —— 把当前数据摘要拼进系统提示词，回答"有多少台设备"这类聚合问题。
 *       这条路**不依赖模型的工具调用能力**，小模型也能答准，是主力。</li>
 *   <li>{@link #definitions()} + {@link #execute} —— 工具调用，让模型自己决定去查什么，
 *       处理"温度传感器 A 保修到什么时候"这类具体查询。</li>
 * </ol>
 *
 * <p><b>数据范围只限设备业务数据（设备 / 分类 / 维修工单），刻意不含用户、角色、菜单。</b>
 * 原因是 5.4 已经给 /api/system/** 加了 @RequireRole("admin")：operator 角色直接调接口会拿到 403。
 * 如果这里开放用户查询，operator 只要问一句"系统里有哪些用户"就能绕过那道防线 ——
 * 等于把刚补上的权限控制从侧门拆掉。工具数量也刻意压到 3 个：3B 小模型面对太多工具更容易选错。
 */
@Component
public class DeviceDataTools {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataTools.class);

    /** 单次工具调用最多返回多少条，避免把大段文本塞进小模型的上下文 */
    private static final int MAX_ROWS = 8;

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepairRepository deviceRepairRepository;

    public DeviceDataTools(DeviceRepository deviceRepository,
                           DeviceCategoryRepository deviceCategoryRepository,
                           DeviceRepairRepository deviceRepairRepository) {
        this.deviceRepository = deviceRepository;
        this.deviceCategoryRepository = deviceCategoryRepository;
        this.deviceRepairRepository = deviceRepairRepository;
    }

    // ============================================================
    // 一、实时数据快照
    // ============================================================

    /**
     * 生成当前数据库的数据摘要，由调用方拼进系统提示词。
     *
     * <p>内容刻意保持简短：小模型上下文有限，塞太多反而会冲淡指令。
     * 所以只放聚合数字和"需要关注"的清单（借出中、维修中），
     * 具体设备明细交给工具去查。
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是设备管理系统的实时数据，回答问题时请以此为准，不要凭空推测：\n");

        sb.append("- 设备总数：").append(deviceRepository.count()).append(" 台\n");

        // 状态分布：直接取数据库 group by 的结果
        List<String> statusParts = new ArrayList<>();
        for (Object[] row : deviceRepository.countGroupByStatus()) {
            statusParts.add(row[0] + " " + ((Number) row[1]).longValue() + " 台");
        }
        sb.append("- 状态分布：")
                .append(statusParts.isEmpty() ? "无数据" : String.join("、", statusParts))
                .append("\n");

        // 分类分布：group by 出来的是 categoryId，换成名称
        Map<Long, String> categoryNames = loadCategoryNames();
        List<String> categoryParts = new ArrayList<>();
        for (Object[] row : deviceRepository.countGroupByCategory()) {
            Long cid = row[0] == null ? null : ((Number) row[0]).longValue();
            String name = cid == null ? "未分类" : categoryNames.getOrDefault(cid, "未知分类");
            categoryParts.add(name + " " + ((Number) row[1]).longValue() + " 台");
        }
        sb.append("- 分类分布：")
                .append(categoryParts.isEmpty() ? "无数据" : String.join("、", categoryParts))
                .append("\n");

        // 借出中的设备（只列前几条，避免膨胀）
        List<Device> borrowed = deviceRepository.findAll(
                (root, query, cb) -> cb.isNotNull(root.get("borrower")),
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.ASC, "id"))).getContent();
        if (borrowed.isEmpty()) {
            sb.append("- 当前借出中：无\n");
        } else {
            sb.append("- 当前借出中（").append(borrowed.size()).append(" 台）：")
                    .append(String.join("、", borrowed.stream()
                            .map(d -> d.getDeviceName() + "（借用人 " + d.getBorrower() + "）")
                            .toList()))
                    .append("\n");
        }

        // 未完工的维修工单。
        // 排除两个终态（已完成 + 已关闭）而不是只排除"已完成"——
        // 否则已关闭的工单会被当成"还在处理中"，快照里的待办数量虚高
        List<DeviceRepair> unfinished = deviceRepairRepository
                .findByRepairStatusNotInOrderByReportTimeDesc(
                        List.of(DeviceRepair.STATUS_FINISHED, DeviceRepair.STATUS_CLOSED),
                        PageRequest.of(0, MAX_ROWS))
                .getContent();
        if (unfinished.isEmpty()) {
            sb.append("- 未完工的维修工单：无\n");
        } else {
            sb.append("- 未完工的维修工单：")
                    .append(String.join("；", unfinished.stream()
                            .map(r -> r.getDeviceName() + "（"
                                    + DeviceRepair.normalizeStatus(r.getRepairStatus())
                                    + "：" + r.getFaultDesc() + "）")
                            .toList()))
                    .append("\n");
        }

        sb.append("- 数据快照时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        return sb.toString();
    }

    // ============================================================
    // 二、工具定义（发给模型）
    // ============================================================

    /** 返回发给 Ollama 的 tools 列表（JSON Schema 格式） */
    public List<Map<String, Object>> definitions() {
        return List.of(
                function("search_devices",
                        "按关键词模糊搜索设备，关键词会同时匹配设备名称、资产编号、序列号。"
                                + "当用户问某台具体设备的信息（保修期、序列号、位置等）时使用。",
                        Map.of("keyword", Map.of(
                                "type", "string",
                                "description", "搜索关键词，例如设备名称或资产编号")),
                        List.of("keyword")),

                function("list_devices_by_status",
                        "按状态列出设备清单。当用户问「哪些设备在维修」「借出去的有哪些」这类"
                                + "需要看清单的问题时使用。如果只是问数量，直接用已知的统计数据回答即可。",
                        Map.of("status", Map.of(
                                "type", "string",
                                "description", "设备状态",
                                "enum", List.of(Device.STATUS_ONLINE, Device.STATUS_OFFLINE,
                                        Device.STATUS_REPAIRING, Device.STATUS_IN_USE))),
                        List.of("status")),

                function("list_repairs",
                        "查询维修工单列表。当用户问维修记录、维修费用、报修人等问题时使用。",
                        Map.of("status", Map.of(
                                "type", "string",
                                "description", "工单状态，不传表示查询全部",
                                "enum", List.of(DeviceRepair.STATUS_PENDING,
                                        DeviceRepair.STATUS_REPAIRING, DeviceRepair.STATUS_FINISHED,
                                        DeviceRepair.STATUS_CLOSED))),
                        List.of())
        );
    }

    private Map<String, Object> function(String name, String description,
                                        Map<String, Object> properties, List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        if (!required.isEmpty()) {
            params.put("required", required);
        }

        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", params);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    // ============================================================
    // 三、执行工具调用
    // ============================================================

    /**
     * 执行模型请求的工具调用，返回给模型看的文本结果。
     *
     * <p>返回纯文本而不是 JSON：小模型读自然语言描述比读结构化数据稳。
     * 任何异常都转成一句可读的话返回，不要让异常中断整轮对话 ——
     * 模型拿到"查询失败"的说明后还能继续组织回答，而不是整个请求崩掉。
     */
    public String execute(String toolName, JsonNode arguments) {
        log.info("AI 请求工具：{}（参数 {}）", toolName, arguments);

        try {
            return switch (toolName == null ? "" : toolName) {
                case "search_devices" -> searchDevices(text(arguments, "keyword"));
                case "list_devices_by_status" -> listByStatus(text(arguments, "status"));
                case "list_repairs" -> listRepairs(text(arguments, "status"));
                default -> "没有名为 " + toolName + " 的工具。请直接使用已有的统计数据回答，或改调其它工具。";
            };
        } catch (Exception e) {
            log.warn("工具 {} 执行失败：{}", toolName, e.getMessage());
            return "查询失败（" + e.getMessage() + "）。请如实告知用户查询未成功，不要编造数据。";
        }
    }

    private String searchDevices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "未提供搜索关键词。";
        }
        String like = "%" + keyword.trim() + "%";
        List<Device> found = queryDevices(null, like);

        if (found.isEmpty()) {
            return "没有找到名称、资产编号或序列号包含「" + keyword + "」的设备。";
        }
        return "找到 " + found.size() + " 台匹配「" + keyword + "」的设备：\n" + formatDevices(found);
    }

    private String listByStatus(String status) {
        if (status == null || status.isBlank()) {
            return "未提供状态参数。";
        }
        List<Device> found = queryDevices(status.trim(), null);

        if (found.isEmpty()) {
            return "没有状态为「" + status + "」的设备。";
        }
        return "状态为「" + status + "」的设备（最多显示 " + MAX_ROWS + " 台）：\n" + formatDevices(found);
    }

    private String listRepairs(String status) {
        // 用 expandStatusFilter 而不是拿 status 直接等值查：
        // 历史工单存的是旧值「待维修」，模型按提示词传「待受理」时会全部查不到。
        // 模型看到"没有符合条件的工单"会如实回答用户，于是用户以为数据不存在 ——
        // 这种"管道通但结果是错的"最难发现
        List<DeviceRepair> repairs = (status == null || status.isBlank())
                ? deviceRepairRepository
                        .findAllByOrderByReportTimeDesc(PageRequest.of(0, MAX_ROWS)).getContent()
                : deviceRepairRepository
                        .findByRepairStatusInOrderByReportTimeDesc(
                                DeviceRepair.expandStatusFilter(status.trim()),
                                PageRequest.of(0, MAX_ROWS))
                        .getContent();

        if (repairs.isEmpty()) {
            return "没有符合条件的维修工单。";
        }

        StringBuilder sb = new StringBuilder("维修工单（最多显示 " + MAX_ROWS + " 条）：\n");
        for (DeviceRepair r : repairs) {
            sb.append("- 工单#").append(r.getId())
                    .append(" ").append(r.getDeviceName())
                    // 展示时把旧值归一，避免同一份回答里出现两种状态说法
                    .append("｜状态：").append(DeviceRepair.normalizeStatus(r.getRepairStatus()))
                    .append("｜故障：").append(r.getFaultDesc());
            appendIfPresent(sb, "报修人", r.getReporter());
            appendIfPresent(sb, "维修人", r.getRepairer());
            if (r.getCost() != null) {
                sb.append("｜费用：").append(r.getCost());
            }
            if (r.getRepairResult() != null && !r.getRepairResult().isBlank()) {
                appendIfPresent(sb, "维修结果", r.getRepairResult());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 辅助
    // ============================================================

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("｜").append(label).append("：").append(value);
        }
    }

    /**
     * 从工具参数里取字符串。
     *
     * <p>做了一层兜底：小模型有时会把参数包成 {"value": "xxx"} 这种嵌套形式，
     * 直接 asString() 会拿到空串，导致工具"查不到东西"而模型却以为真没有。
     */
    private String text(JsonNode args, String field) {
        if (args == null) {
            return null;
        }
        JsonNode node = args.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode inner = node.path("value");
            return inner.isMissingNode() ? node.asString() : inner.asString();
        }
        return node.asString();
    }

    /** 设备查询：状态精确匹配 + 关键词模糊匹配，最多 MAX_ROWS 条 */
    private List<Device> queryDevices(String status, String like) {
        Specification<Device> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (like != null) {
                predicates.add(cb.or(
                        cb.like(root.get("deviceName"), like),
                        cb.like(root.get("assetCode"), like),
                        cb.like(root.get("serialNumber"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return deviceRepository
                .findAll(spec, PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();
    }

    private Map<Long, String> loadCategoryNames() {
        return deviceCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCategory::getId, DeviceCategory::getCategoryName,
                        (a, b) -> a));
    }

    private String formatDevices(List<Device> devices) {
        Map<Long, String> categoryNames = loadCategoryNames();
        Function<Device, String> line = d -> {
            StringBuilder sb = new StringBuilder("- ").append(d.getDeviceName());
            appendIfPresent(sb, "资产编号", d.getAssetCode());
            sb.append("｜状态：").append(d.getStatus());
            if (d.getCategoryId() != null) {
                sb.append("｜分类：").append(categoryNames.getOrDefault(d.getCategoryId(), "未知分类"));
            }
            appendIfPresent(sb, "位置", d.getLocation());
            if (d.getWarrantyDate() != null) {
                sb.append("｜保修到期：").append(d.getWarrantyDate());
            }
            appendIfPresent(sb, "当前借用人", d.getBorrower());
            return sb.append("\n").toString();
        };
        return devices.stream().map(line).collect(Collectors.joining());
    }
}
