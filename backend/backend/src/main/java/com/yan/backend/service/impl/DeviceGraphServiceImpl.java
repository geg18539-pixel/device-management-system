package com.yan.backend.service.impl;

import com.yan.backend.dto.DeviceGraphVO;
import com.yan.backend.dto.GraphEdgeVO;
import com.yan.backend.dto.GraphNodeVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceMaintenancePlan;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;
import com.yan.backend.entity.SysDept;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.DeviceMaintenancePlanRepository;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.SparePartRecordRepository;
import com.yan.backend.repository.SparePartRepository;
import com.yan.backend.repository.SysDeptRepository;
import com.yan.backend.service.DeviceGraphService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备关系图谱的组装。
 *
 * <h3>节点数是**受控的**，这是能画清楚的前提</h3>
 *
 * <p>工单只取最近 {@value #MAX_REPAIRS} 张、配件只取 {@value #MAX_PARTS} 种。
 * 一台用了五年的设备可能有几十张工单，全画上去就是一团毛线，
 * 既看不出结构也点不中节点。被截断时会在 {@code note} 里写明"一共多少、画了多少" ——
 * 不写的话用户会以为"这台设备就修过 5 次"。
 *
 * <h3>只有 6 次查询，和关联数据量无关</h3>
 *
 * <p>部门 / 分类 / 工单 / 保养计划 / 领料记录 / 配件各查一次，
 * 没有"逐个工单去查它的配件"那种 N+1。
 */
@Service
public class DeviceGraphServiceImpl implements DeviceGraphService {

    /**
     * 各类节点的上限。
     *
     * <p>这几个数字是**配套的** —— 它们加起来决定了一屏最多有多少个节点。
     * 当前是 1 + 1 + 1 + 1 + 4 + 6 + 4 = 18 个，力导向布局下这个量级还看得清
     * 每个节点的名字；再往上节点就开始互相压字、边也缠到一起了。
     * **改大任何一个之前，先算一下总数会不会超过 20 左右。**
     */
    private static final int MAX_REPAIRS = 4;
    private static final int MAX_PARTS = 6;
    private static final int MAX_SIBLINGS = 4;

    // ---------- 语义色。**只给语义，色值由前端按主题决定**，理由见 GraphNodeVO ----------
    private static final String TONE_PRIMARY = "primary";
    private static final String TONE_PRIMARY_SOFT = "primarySoft";
    private static final String TONE_OK = "ok";
    private static final String TONE_WARN = "warn";
    private static final String TONE_CRIT = "crit";
    private static final String TONE_IDLE = "idle";

    /** 节点类型的中文名 */
    private static final String TYPE_DEVICE = "device";
    private static final String TYPE_DEPT = "dept";
    private static final String TYPE_CATEGORY = "category";
    private static final String TYPE_REPAIR = "repair";
    private static final String TYPE_MAINTENANCE = "maintenance";
    private static final String TYPE_PART = "part";

    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository categoryRepository;
    private final SysDeptRepository deptRepository;
    private final DeviceRepairRepository repairRepository;
    private final DeviceMaintenancePlanRepository planRepository;
    private final SparePartRecordRepository partRecordRepository;
    private final SparePartRepository sparePartRepository;

    public DeviceGraphServiceImpl(DeviceRepository deviceRepository,
                                  DeviceCategoryRepository categoryRepository,
                                  SysDeptRepository deptRepository,
                                  DeviceRepairRepository repairRepository,
                                  DeviceMaintenancePlanRepository planRepository,
                                  SparePartRecordRepository partRecordRepository,
                                  SparePartRepository sparePartRepository) {
        this.deviceRepository = deviceRepository;
        this.categoryRepository = categoryRepository;
        this.deptRepository = deptRepository;
        this.repairRepository = repairRepository;
        this.planRepository = planRepository;
        this.partRecordRepository = partRecordRepository;
        this.sparePartRepository = sparePartRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceGraphVO graph(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("设备不存在：" + deviceId));

        DeviceGraphVO vo = new DeviceGraphVO();
        vo.setDeviceId(device.getId());
        vo.setDeviceName(device.getDeviceName());

        List<GraphNodeVO> nodes = new ArrayList<>();
        List<GraphEdgeVO> edges = new ArrayList<>();

        String centerId = nodeId(TYPE_DEVICE, device.getId());
        nodes.add(deviceNode(device, centerId));

        addDept(device, centerId, nodes, edges);
        addSiblingDevices(device, centerId, nodes, edges, vo);
        addCategory(device, centerId, nodes, edges);
        addMaintenancePlan(device, centerId, nodes, edges);
        addRepairsAndParts(device, centerId, nodes, edges, vo);

        vo.setNodes(nodes);
        vo.setEdges(edges);
        vo.setNote(buildNote(vo));
        return vo;
    }

    // ============================================================
    // 各类节点
    // ============================================================

    private GraphNodeVO deviceNode(Device device, String id) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(device.getAssetCode())) {
            parts.add(device.getAssetCode());
        }
        if (StringUtils.hasText(device.getStatus())) {
            parts.add(device.getStatus());
        }
        if (StringUtils.hasText(device.getLifecycleStatus())) {
            parts.add(device.getLifecycleStatus());
        }

        GraphNodeVO node = simpleNode(id, device.getDeviceName(), TYPE_DEVICE, "设备",
                TONE_PRIMARY, String.join("｜", parts));
        node.setCenter(true);
        // 中心节点可点：点它就是"回到这台设备"，前端会重新以它为中心重画
        node.setRouteType(TYPE_DEVICE);
        node.setRouteId(device.getId());
        return node;
    }

    private void addDept(Device device, String centerId,
                         List<GraphNodeVO> nodes, List<GraphEdgeVO> edges) {
        if (device.getDeptId() == null) {
            return;
        }
        SysDept dept = deptRepository.findById(device.getDeptId()).orElse(null);
        if (dept == null) {
            // 部门被删了但设备还挂着那个 id —— 不报错，只是这条边画不出来。
            // 设备的 deptId 是裸 id 不是外键，这种情况是可能的
            return;
        }
        String id = nodeId(TYPE_DEPT, dept.getId());
        nodes.add(simpleNode(id, dept.getDeptName(), TYPE_DEPT, "部门", TONE_PRIMARY_SOFT, null));
        edges.add(new GraphEdgeVO(centerId, id, "所属部门"));
    }

    /**
     * 同部门的其他设备。
     *
     * <p><b>没有它这张图是"走不动"的</b>：以一台设备为中心的 1 跳邻居里
     * 全是部门 / 分类 / 工单 / 配件，一个别的设备都没有，
     * 用户看完一台只能回到下拉框里重新搜。加上同部门设备之后，
     * 图才真的可以"沿着关系走过去" —— 点旁边的设备，以它为中心重新展开。
     *
     * <p>边的名字写「同部门」而不是「相关」：关系说不清楚的时候，
     * 用户会以为系统知道一些它其实不知道的事情。
     */
    private void addSiblingDevices(Device device, String centerId,
                                   List<GraphNodeVO> nodes, List<GraphEdgeVO> edges,
                                   DeviceGraphVO vo) {
        if (device.getDeptId() == null) {
            vo.setSiblingTotal(0);
            vo.setSiblingShown(0);
            return;
        }

        Page<Device> page = deviceRepository.findByDeptIdAndIdNotOrderByIdAsc(
                device.getDeptId(), device.getId(), PageRequest.of(0, MAX_SIBLINGS));

        vo.setSiblingTotal((int) page.getTotalElements());
        vo.setSiblingShown(page.getContent().size());

        for (Device sibling : page.getContent()) {
            String id = nodeId(TYPE_DEVICE, sibling.getId());
            GraphNodeVO node = simpleNode(id, sibling.getDeviceName(), TYPE_DEVICE, "设备",
                    TONE_PRIMARY_SOFT, sibling.getStatus());
            // 可点：前端会以这台设备为中心重新画
            node.setRouteType(TYPE_DEVICE);
            node.setRouteId(sibling.getId());
            nodes.add(node);
            edges.add(new GraphEdgeVO(centerId, id, "同部门"));
        }
    }

    private void addCategory(Device device, String centerId,
                             List<GraphNodeVO> nodes, List<GraphEdgeVO> edges) {
        if (device.getCategoryId() == null) {
            return;
        }
        DeviceCategory category = categoryRepository.findById(device.getCategoryId()).orElse(null);
        if (category == null) {
            return;
        }
        String id = nodeId(TYPE_CATEGORY, category.getId());
        nodes.add(simpleNode(id, category.getCategoryName(), TYPE_CATEGORY, "分类", TONE_IDLE, null));
        edges.add(new GraphEdgeVO(centerId, id, "分类"));
    }

    private void addMaintenancePlan(Device device, String centerId,
                                    List<GraphNodeVO> nodes, List<GraphEdgeVO> edges) {
        DeviceMaintenancePlan plan = planRepository.findByDeviceId(device.getId()).orElse(null);
        if (plan == null) {
            return;
        }

        LocalDate next = plan.getNextMaintenanceDate();
        String subtitle;
        String tone;
        if (DeviceMaintenancePlan.STATUS_DISABLED.equals(plan.getStatus())) {
            subtitle = "已停用";
            tone = TONE_IDLE;
        } else if (next == null) {
            subtitle = "未设到期日";
            tone = TONE_IDLE;
        } else {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), next);
            subtitle = days < 0 ? "已逾期 " + (-days) + " 天" : "下次 " + next;
            // 逾期是这张图上唯一"需要马上动手"的信号，给它告警色
            tone = days < 0 ? TONE_WARN : TONE_OK;
        }

        String id = nodeId(TYPE_MAINTENANCE, plan.getId());
        nodes.add(simpleNode(id, plan.getPlanName(), TYPE_MAINTENANCE, "保养计划", tone, subtitle));
        edges.add(new GraphEdgeVO(centerId, id, "保养计划"));
    }

    /**
     * 工单和它们的配件。
     *
     * <p>配件是挂在**工单**上的（不是直接挂设备）：一张工单领了哪几种件，
     * 所以边上写的是"领用"。这样"这台设备哪次维修换了什么"是能读出来的，
     * 直接连到设备上就把这层信息丢了。
     */
    private void addRepairsAndParts(Device device, String centerId,
                                    List<GraphNodeVO> nodes, List<GraphEdgeVO> edges,
                                    DeviceGraphVO vo) {
        Page<DeviceRepair> page = repairRepository.findByDeviceIdOrderByReportTimeDesc(
                device.getId(), PageRequest.of(0, MAX_REPAIRS));

        List<DeviceRepair> repairs = page.getContent();
        vo.setRepairTotal((int) page.getTotalElements());
        vo.setRepairShown(repairs.size());

        if (repairs.isEmpty()) {
            vo.setPartTotal(0);
            vo.setPartShown(0);
            return;
        }

        for (DeviceRepair repair : repairs) {
            String id = nodeId(TYPE_REPAIR, repair.getId());
            nodes.add(repairNode(repair, id));
            edges.add(new GraphEdgeVO(centerId, id, "报修"));
        }

        addParts(repairs, nodes, edges, vo);
    }

    private GraphNodeVO repairNode(DeviceRepair repair, String id) {
        String status = DeviceRepair.normalizeStatus(repair.getRepairStatus());

        // 节点上显示**故障描述**而不是工单号：人认得出"主轴异响"，
        // 认不出"工单 #7"。工单号放在副标题里，需要精确指代时用得上
        String name = StringUtils.hasText(repair.getFaultDesc())
                ? truncate(repair.getFaultDesc(), 12)
                : "工单 #" + repair.getId();

        GraphNodeVO node = simpleNode(id, name, TYPE_REPAIR, "维修工单",
                repairTone(status), status + "｜#" + repair.getId());
        node.setRouteType(TYPE_REPAIR);
        node.setRouteId(repair.getId());
        return node;
    }

    /**
     * 未完结的工单用告警色。
     *
     * <p>这是整张图上信息量最大的一处配色 —— 设备档案页要一眼看出
     * "这台设备现在还有没有没处理完的活"。已完成用正常色、已关闭用中性色。
     */
    private String repairTone(String normalizedStatus) {
        if (DeviceRepair.STATUS_FINISHED.equals(normalizedStatus)) {
            return TONE_OK;
        }
        if (DeviceRepair.STATUS_CLOSED.equals(normalizedStatus)) {
            return TONE_IDLE;
        }
        // 待受理 / 维修中
        return TONE_WARN;
    }

    private void addParts(List<DeviceRepair> repairs, List<GraphNodeVO> nodes,
                          List<GraphEdgeVO> edges, DeviceGraphVO vo) {
        List<Long> repairIds = repairs.stream().map(DeviceRepair::getId).toList();
        List<SparePartRecord> records =
                partRecordRepository.findByRelatedRepairIdInOrderByRecordTimeDesc(repairIds);

        // 按出现顺序去重（记录已经按时间倒序，所以第一个出现的即最近一次领用）：
        // 同一张工单可能分两次领同一个件，图上只画一个节点、一条边
        Map<Long, SparePartRecord> firstByPart = new LinkedHashMap<>();
        for (SparePartRecord r : records) {
            if (r.getPartId() != null) {
                firstByPart.putIfAbsent(r.getPartId(), r);
            }
        }
        vo.setPartTotal(firstByPart.size());

        if (firstByPart.isEmpty()) {
            vo.setPartShown(0);
            return;
        }

        // 一次把配件主数据查出来 —— 记录里只有名称快照，没有当前库存
        Map<Long, SparePart> partsById = sparePartRepository.findAllById(firstByPart.keySet())
                .stream().collect(Collectors.toMap(SparePart::getId, Function.identity()));

        int shown = 0;
        for (Map.Entry<Long, SparePartRecord> entry : firstByPart.entrySet()) {
            if (shown >= MAX_PARTS) {
                break;
            }
            Long partId = entry.getKey();
            SparePartRecord record = entry.getValue();
            SparePart part = partsById.get(partId);

            String id = nodeId(TYPE_PART, partId);
            String name = part != null ? part.getPartName() : record.getPartName();

            String subtitle = null;
            String tone = TONE_PRIMARY_SOFT;
            boolean low = false;
            if (part != null) {
                int stock = part.getStockQuantity() == null ? 0 : part.getStockQuantity();
                int threshold = part.getWarnThreshold() == null ? 0 : part.getWarnThreshold();
                low = stock <= threshold;
                subtitle = "库存 " + stock + (StringUtils.hasText(part.getUnit()) ? part.getUnit() : "");
                // 库存告急用故障色 —— 和看板、消息里的口径完全一致
                tone = low ? TONE_CRIT : TONE_PRIMARY_SOFT;
            } else {
                // 配件主数据已经没了（被删过），但工单上的领用记录还在。
                // 照样画出来，只是没有当前库存
                subtitle = "配件已删除";
                tone = TONE_IDLE;
            }

            GraphNodeVO node = simpleNode(id, name, TYPE_PART, "配件", tone, subtitle);
            node.setRouteType(TYPE_PART);
            node.setRouteId(partId);
            nodes.add(node);
            edges.add(new GraphEdgeVO(nodeId(TYPE_REPAIR, record.getRelatedRepairId()), id, "领用"));
            shown++;
        }
        vo.setPartShown(shown);
    }

    // ============================================================
    // 工具
    // ============================================================

    private static String nodeId(String type, Long id) {
        // 必须带类型前缀：ECharts 用节点名做索引，"设备 1"和"工单 1"不带前缀就撞了
        return type + ":" + id;
    }

    private static GraphNodeVO simpleNode(String id, String name, String nodeType,
                                          String typeLabel, String tone, String subtitle) {
        GraphNodeVO node = new GraphNodeVO();
        node.setId(id);
        node.setName(name);
        node.setNodeType(nodeType);
        node.setTypeLabel(typeLabel);
        node.setTone(tone);
        node.setSubtitle(subtitle);
        return node;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    /** 截断时给用户看的一句话。空串表示什么都没截 */
    private String buildNote(DeviceGraphVO vo) {
        List<String> clauses = new ArrayList<>();
        if (vo.getRepairTotal() > vo.getRepairShown()) {
            clauses.add("工单只画了最近 " + vo.getRepairShown() + " 张（共 " + vo.getRepairTotal() + " 张）");
        }
        if (vo.getPartTotal() > vo.getPartShown()) {
            clauses.add("配件只画了最近用过的 " + vo.getPartShown() + " 种（共 " + vo.getPartTotal() + " 种）");
        }
        if (vo.getSiblingTotal() > vo.getSiblingShown()) {
            clauses.add("同部门设备只画了 " + vo.getSiblingShown() + " 台（共 " + vo.getSiblingTotal() + " 台）");
        }
        return clauses.isEmpty() ? "" : String.join("；", clauses) + "。";
    }
}
