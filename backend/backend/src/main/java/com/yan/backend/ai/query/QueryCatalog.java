package com.yan.backend.ai.query;

import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SparePart;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自然语言问数的**白名单目录** —— 整个功能的唯一可信来源。
 *
 * <h3>为什么是"受控问数"而不是让模型自由写 SQL</h3>
 *
 * <p>模型能产出的东西只有**这张目录里的枚举键**（指标、维度、筛选字段和取值），
 * 一句 SQL 都产不出来。SQL（准确说是 JPQL）由 {@link QueryExecutor} 拿这些键
 * 去目录里取**预先写死的片段**拼出来，值一律走绑定参数。
 *
 * <p>这样做的结果：
 * <ul>
 *   <li><b>没有注入面</b> —— 模型碰不到任何一个 SQL 片段，它只能从固定集合里挑字符串；
 *       挑错了就直接被拒（见 {@link QueryPlan} 的校验），不会传到查询层。</li>
 *   <li><b>不需要第二个数据源</b> —— 常见的做法是给问数配一个只读数据库账号，
 *       但那要求应用同时持有两个 DataSource，是一整块架构改动。
 *       这里的查询永远是"对白名单实体的只读聚合"，那个账号也就没有必要了。</li>
 *   <li><b>不需要分库方言适配</b> —— 拼的是 JPQL，方言交给 Hibernate 翻译，
 *       H2（测试）和 MySQL（生产）走同一份代码。</li>
 * </ul>
 *
 * <p><b>代价</b>：只能问目录里有的角度。加一个角度 = 往这张表里加一条，
 * 不需要动规划器和执行器 —— 这是刻意换来的"扩展成本低、出错面窄"。
 *
 * <h3>四个不变量</h3>
 * <ol>
 *   <li>目录里出现的每一个表达式都是**我们写死的**，没有任何一处来自模型；</li>
 *   <li>只读 —— 这里没有、也不会有任何写操作；</li>
 *   <li>只碰业务数据：设备、工单、配件。**不含用户、角色、菜单** ——
 *       和 AI 快照、AI 工具是同一条口径，否则普通操作员问一句
 *       "系统里有哪些用户"就绕过了 {@code @RequireRole("admin")} 那道防线；</li>
 *   <li>结果集上限由执行器强制，目录里不需要也不应该关心。</li>
 * </ol>
 */
@Component
public class QueryCatalog {

    /** 查询形态 */
    public enum Mode {
        /** 聚合：一个数，或者按维度分组后的几个数 */
        AGGREGATE,
        /** 清单：列出明细行（取前若干条） */
        LIST
    }

    /** 字段取值怎么处理 */
    public enum ValueKind {
        /** 原样显示 */
        TEXT,
        /** 存的是部门 id，要换成部门名 */
        DEPT_ID,
        /** 存的是分类 id，要换成分类名 */
        CATEGORY_ID
    }

    /** 筛选字段的类型，决定提示词怎么写、执行器怎么绑参数 */
    public enum FilterKind {
        /** 模糊匹配（设备名 / 资产编号） */
        KEYWORD,
        /** 固定几个取值里选一个 */
        ENUM,
        /** 时间范围，取值见 {@link #TIME_RANGES} */
        TIME_RANGE
    }

    /**
     * 一个可用的字段。
     *
     * <p>**同一个概念两用**：聚合查询里它是分组维度，清单查询里它是输出的一列。
     * 拆成两个类型的话，两边的定义会慢慢分叉（同一个字段在分组里叫"部门"、
     * 在列里叫"所属部门"），而它们本该是同一个东西。
     *
     * @param expr 必须是**实体属性路径**（如 {@code d.deptId}）。
     *             这里只是拼进 JPQL 的字符串，实际拼装前会先确认它确实来自目录 ——
     *             模型的输出永远不会直接进到这个位置
     */
    public record Field(String key, String label, String expr, ValueKind kind) {}

    /** 枚举筛选的一个可选值 */
    public record Option(String value, String label) {}

    /**
     * @param exprs 这个筛选作用在哪些字段上。**多数情况只有一个**；
     *              关键词是例外 —— 它要同时匹配设备名和资产编号（用户搜
     *              「ZC-2026-0001」和搜「温度传感器」都得管用），所以是多个，
     *              执行器会把它们用 OR 连起来
     */
    public record Filter(String key, String label, FilterKind kind, List<String> exprs,
                         List<Option> options) {}

    /** 时间范围的取值 */
    public record TimeRange(String key, String label) {}

    /**
     * @param from       JPQL 的 from 子句。**只允许单一实体，不写 join** ——
     *                   多一个 join 就多一处方言和性能的不确定性，而这几项需求
     *                   都能在单实体上满足
     * @param selectExpr 聚合表达式。清单形态下为 null
     * @param fixedWhere 固定的过滤条件（不含模型输入）。null 表示没有
     * @param columns    清单形态下要输出的列。聚合形态下为 null
     * @param orderBy    清单形态下的排序表达式。聚合形态下为 null（聚合一律按
     *                   数值倒序，那正是"谁最多"这个问题想问的）。
     *                   **必须是稳定序**，否则同一份数据两次查询顺序不同，
     *                   用户会以为数据在变
     */
    public record Metric(String key, String label, String hint, Mode mode,
                         String from, String selectExpr, String fixedWhere,
                         List<Field> dimensions, List<Filter> filters, List<Field> columns,
                         String orderBy) {}

    // ============================================================
    // 可选值
    // ============================================================

    private static final List<Option> LIFECYCLE_OPTIONS = List.of(
            new Option(Device.LIFECYCLE_NORMAL, "正常"),
            new Option(Device.LIFECYCLE_REPAIR, "维修"),
            new Option(Device.LIFECYCLE_SCRAPPED, "报废"),
            new Option(Device.LIFECYCLE_DISABLED, "停用"));

    private static final List<Option> CONN_OPTIONS = List.of(
            new Option(Device.STATUS_ONLINE, "在线"),
            new Option(Device.STATUS_OFFLINE, "离线"),
            new Option(Device.STATUS_REPAIRING, "维修中"),
            new Option(Device.STATUS_IN_USE, "使用中"));

    private static final List<Option> REPAIR_STATUS_OPTIONS = List.of(
            new Option(DeviceRepair.STATUS_PENDING, "待受理"),
            new Option(DeviceRepair.STATUS_REPAIRING, "维修中"),
            new Option(DeviceRepair.STATUS_FINISHED, "已完成"),
            new Option(DeviceRepair.STATUS_CLOSED, "已关闭"));

    /** 时间范围。执行器负责把 key 翻成真实的起止时刻 */
    public static final List<TimeRange> TIME_RANGES = List.of(
            new TimeRange("TODAY", "今天"),
            new TimeRange("LAST_7_DAYS", "近 7 天"),
            new TimeRange("LAST_30_DAYS", "近 30 天"),
            new TimeRange("THIS_MONTH", "本月"),
            new TimeRange("LAST_MONTH", "上个月"),
            new TimeRange("THIS_YEAR", "今年"),
            new TimeRange("ALL", "全部（不限时间）"));

    // ============================================================
    // 设备上的常用字段（几个指标共用）
    // ============================================================

    private static final Field DEVICE_NAME = new Field("deviceName", "设备", "d.deviceName", ValueKind.TEXT);
    private static final Field DEVICE_DEPT = new Field("dept", "部门", "d.deptId", ValueKind.DEPT_ID);
    private static final Field DEVICE_CATEGORY = new Field("category", "分类", "d.categoryId", ValueKind.CATEGORY_ID);
    private static final Field DEVICE_LIFECYCLE = new Field("lifecycle", "资产状态", "d.lifecycleStatus", ValueKind.TEXT);
    private static final Field DEVICE_CONN = new Field("connStatus", "连通状态", "d.status", ValueKind.TEXT);
    private static final Field DEVICE_CODE = new Field("assetCode", "资产编号", "d.assetCode", ValueKind.TEXT);

    private static final Filter FILTER_KEYWORD = new Filter("keyword", "关键词", FilterKind.KEYWORD,
            List.of("d.deviceName", "d.assetCode"), null);
    private static final Filter FILTER_LIFECYCLE = new Filter("lifecycle", "资产状态", FilterKind.ENUM,
            List.of("d.lifecycleStatus"), LIFECYCLE_OPTIONS);
    private static final Filter FILTER_CONN = new Filter("connStatus", "连通状态", FilterKind.ENUM,
            List.of("d.status"), CONN_OPTIONS);

    private static final Filter REPAIR_FILTER_TIME = new Filter("timeRange", "时间范围", FilterKind.TIME_RANGE,
            List.of("r.reportTime"), null);
    private static final Filter REPAIR_FILTER_STATUS = new Filter("repairStatus", "工单状态", FilterKind.ENUM,
            List.of("r.repairStatus"), REPAIR_STATUS_OPTIONS);

    // ============================================================
    // 目录本体
    // ============================================================

    private final List<Metric> metrics = List.of(

            new Metric("device_count", "设备数量", "统计设备台数。可以按部门、分类、资产状态、连通状态分组",
                    Mode.AGGREGATE,
                    "Device d", "count(d)", null,
                    List.of(DEVICE_DEPT, DEVICE_CATEGORY, DEVICE_LIFECYCLE, DEVICE_CONN),
                    List.of(FILTER_KEYWORD, FILTER_LIFECYCLE, FILTER_CONN),
                    null, null),

            new Metric("repair_count", "维修工单数", "统计维修工单条数。可以按工单状态或设备分组",
                    Mode.AGGREGATE,
                    "DeviceRepair r", "count(r)", null,
                    List.of(
                            new Field("repairStatus", "工单状态", "r.repairStatus", ValueKind.TEXT),
                            new Field("device", "设备", "r.deviceName", ValueKind.TEXT)),
                    List.of(REPAIR_FILTER_TIME, REPAIR_FILTER_STATUS),
                    null, null),

            new Metric("repair_cost", "维修费用合计", "统计维修花了多少钱（把每张工单的费用加起来）",
                    Mode.AGGREGATE,
                    "DeviceRepair r", "coalesce(sum(r.cost), 0)", null,
                    List.of(
                            new Field("device", "设备", "r.deviceName", ValueKind.TEXT),
                            new Field("repairStatus", "工单状态", "r.repairStatus", ValueKind.TEXT)),
                    List.of(REPAIR_FILTER_TIME, REPAIR_FILTER_STATUS),
                    null, null),

            new Metric("device_list", "设备清单", "列出符合条件的设备（问「哪些设备…」时用这个）",
                    Mode.LIST,
                    "Device d", null, null,
                    null,
                    List.of(FILTER_KEYWORD, FILTER_LIFECYCLE, FILTER_CONN),
                    List.of(DEVICE_NAME, DEVICE_CODE, DEVICE_CONN, DEVICE_LIFECYCLE,
                            DEVICE_DEPT, DEVICE_CATEGORY),
                    // 新设备在前：问「哪些设备…」的人多半关心最近建的/最近变动的
                    "d.id desc"),

            new Metric("low_stock_part_list", "库存告急配件", "列出库存已经降到预警阈值以下的配件",
                    Mode.LIST,
                    "SparePart p", null,
                    // 固定条件，不含任何模型输入。用的判据和看板的 lowStockCount 完全一致
                    "p.status = '" + SparePart.STATUS_ENABLED + "' and p.stockQuantity <= p.warnThreshold",
                    null,
                    List.of(),
                    List.of(
                            new Field("partName", "配件", "p.partName", ValueKind.TEXT),
                            new Field("partCode", "编码", "p.partCode", ValueKind.TEXT),
                            new Field("stock", "当前库存", "p.stockQuantity", ValueKind.TEXT),
                            new Field("threshold", "预警阈值", "p.warnThreshold", ValueKind.TEXT)),
                    // 缺得最狠的排最前 —— 那是最该先去补的
                    "p.stockQuantity asc")
    );

    /**
     * 示例问题。
     *
     * <p>放在目录里而不是写死在前端：这些句子必须**一一对应得上目录里的指标**，
     * 分开维护必然出现"示例里能问、点进去却报不支持"。加了新指标顺手在这里补一句。
     */
    private static final List<String> EXAMPLES = List.of(
            "一共有多少台设备",
            "各部门分别有多少台设备",
            "现在哪些设备在维修中",
            "本月有多少张维修工单",
            "各设备的维修费用合计是多少",
            "哪些配件库存告急了");

    // ============================================================

    public List<String> exampleQuestions() {
        return EXAMPLES;
    }

    public List<Metric> allMetrics() {
        return metrics;
    }

    /** 按 key 找指标。找不到返回 null —— 调用方负责转成"没理解你的问题" */
    public Metric find(String key) {
        if (key == null) {
            return null;
        }
        return metrics.stream().filter(m -> m.key().equals(key)).findFirst().orElse(null);
    }
}
