package com.yan.backend.ai.query;

import com.yan.backend.dto.QueryResultVO;
import com.yan.backend.entity.DeviceCategory;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.entity.SysDept;
import com.yan.backend.repository.DeviceCategoryRepository;
import com.yan.backend.repository.SysDeptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把 {@link QueryPlan} 变成一次真实的查询。
 *
 * <h3>模型碰不到这里的任何一个字符</h3>
 *
 * <p>拼进 JPQL 的每一段 —— 实体、属性路径、聚合表达式、排序 ——
 * 都来自 {@link QueryCatalog} 里写死的常量。{@link QueryPlan} 提供的只有
 * **对象引用**（指向目录里的 {@code Field} / {@code Filter}）和**绑定参数的值**，
 * 而值只出现在 {@code :p0} 这样的占位符上，永远不会被拼进语句。
 *
 * <p>这就是"受控问数"省掉那几道闸的原因：不需要表白名单校验（表是写死的）、
 * 不需要禁多语句（一次只发一条 JPQL）、不需要只读账号（这里永远只有 select）。
 * 唯一要守住的是**结果集上限**，由本类强制。
 *
 * <h3>为什么用 JPQL 而不是原生 SQL</h3>
 *
 * <p>方言交给 Hibernate 翻译，H2（测试）和 MySQL（生产）走同一份代码 ——
 * 手写 SQL 的话，`date_format` 这类函数两边对不上，只有真跑起来才发现。
 */
@Component
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    /**
     * 一次最多返回多少行。
     *
     * <p>这个上限是**产品决定**不是技术限制：问数要的是"看个大概"，
     * 50 行足够。真正的明细查询该去设备管理、配件耗材那些页面做筛选和分页。
     * 截断时会在结果里说明，不静默。
     */
    private static final int MAX_ROWS = 50;

    /** 部门/分类被删掉后，设备上那个 id 会指向不存在的东西 */
    private static final String UNKNOWN_DEPT = "未分配";
    private static final String UNKNOWN_CATEGORY = "未分类";

    private final EntityManager entityManager;
    private final SysDeptRepository deptRepository;
    private final DeviceCategoryRepository categoryRepository;

    public QueryExecutor(EntityManager entityManager,
                         SysDeptRepository deptRepository,
                         DeviceCategoryRepository categoryRepository) {
        this.entityManager = entityManager;
        this.deptRepository = deptRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public QueryResultVO execute(String question, QueryPlan plan) {
        QueryCatalog.Metric metric = plan.metric();

        // 名称映射：维度里存的是部门/分类的 id，直接显示数字用户看不懂。
        // 部门分类都是几十条的规模，整表读一次比逐行查便宜得多
        Map<Long, String> deptNames = deptRepository.findAll().stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(DeviceCategory::getId, DeviceCategory::getCategoryName,
                        (a, b) -> a));

        Map<String, Object> params = new LinkedHashMap<>();
        String where = buildConditions(metric, plan.filters(), params);

        QueryResultVO vo = new QueryResultVO();
        vo.setQuestion(question);
        vo.setUnderstanding(plan.understanding());
        vo.setMetricLabel(metric.label());
        vo.setGroupByLabel(plan.groupBy() == null ? null : plan.groupBy().label());
        vo.setListMode(metric.mode() == QueryCatalog.Mode.LIST);

        if (metric.mode() == QueryCatalog.Mode.LIST) {
            runList(metric, where, params, deptNames, categoryNames, vo);
        } else {
            runAggregate(metric, plan.groupBy(), where, params, deptNames, categoryNames, vo);
        }

        log.info("问数执行：指标={}，返回 {} 行{}", metric.key(), vo.getRowCount(),
                vo.isTruncated() ? "（已截断）" : "");
        return vo;
    }

    // ============================================================
    // 聚合
    // ============================================================

    private void runAggregate(QueryCatalog.Metric metric, QueryCatalog.Field groupBy,
                              String where, Map<String, Object> params,
                              Map<Long, String> deptNames, Map<Long, String> categoryNames,
                              QueryResultVO vo) {
        String jpql;
        boolean grouped = groupBy != null;

        if (grouped) {
            // 按数值倒序 —— "哪个部门最多"正是这个问题想问的
            jpql = "select " + groupBy.expr() + ", " + metric.selectExpr()
                    + " from " + metric.from() + where
                    + " group by " + groupBy.expr()
                    + " order by " + metric.selectExpr() + " desc";
        } else {
            jpql = "select " + metric.selectExpr() + " from " + metric.from() + where;
        }

        Query query = entityManager.createQuery(jpql);
        bind(query, params);
        query.setMaxResults(grouped ? MAX_ROWS + 1 : 1);

        List<?> raw = query.getResultList();

        List<List<String>> rows = new ArrayList<>();
        if (grouped) {
            vo.setColumns(List.of(groupBy.label(), metric.label()));
            boolean truncated = raw.size() > MAX_ROWS;
            for (Object item : truncated ? raw.subList(0, MAX_ROWS) : raw) {
                Object[] pair = (Object[]) item;
                rows.add(List.of(labelOf(pair[0], groupBy.kind(), deptNames, categoryNames),
                        format(pair[1])));
            }
            vo.setTruncated(truncated);
        } else {
            vo.setColumns(List.of(metric.label()));
            Object value = raw.isEmpty() ? null : raw.get(0);
            rows.add(List.of(format(value)));
        }

        vo.setRows(rows);
        vo.setRowCount(rows.size());
        vo.setSummary(summarizeAggregate(metric, groupBy, rows, vo.isTruncated()));
    }

    // ============================================================
    // 清单
    // ============================================================

    private void runList(QueryCatalog.Metric metric, String where, Map<String, Object> params,
                         Map<Long, String> deptNames, Map<Long, String> categoryNames,
                         QueryResultVO vo) {
        List<QueryCatalog.Field> cols = metric.columns();
        List<String> selectParts = new ArrayList<>();
        for (QueryCatalog.Field f : cols) {
            selectParts.add(f.expr());
        }

        String jpql = "select " + String.join(", ", selectParts)
                + " from " + metric.from() + where
                + (metric.orderBy() == null ? "" : " order by " + metric.orderBy());

        Query query = entityManager.createQuery(jpql);
        bind(query, params);
        // 多取一行用来判断"是不是被截断了"，比再发一条 count 便宜
        query.setMaxResults(MAX_ROWS + 1);

        List<?> raw = query.getResultList();
        boolean truncated = raw.size() > MAX_ROWS;

        List<String> headers = cols.stream().map(QueryCatalog.Field::label).toList();
        List<List<String>> rows = new ArrayList<>();
        for (Object item : truncated ? raw.subList(0, MAX_ROWS) : raw) {
            Object[] values = item instanceof Object[] arr ? arr : new Object[]{item};
            List<String> row = new ArrayList<>();
            for (int i = 0; i < cols.size(); i++) {
                Object v = i < values.length ? values[i] : null;
                row.add(labelOf(v, cols.get(i).kind(), deptNames, categoryNames));
            }
            rows.add(row);
        }

        vo.setColumns(headers);
        vo.setRows(rows);
        vo.setRowCount(rows.size());
        vo.setTruncated(truncated);
        vo.setSummary(truncated
                ? "结果超过 " + MAX_ROWS + " 条，只显示前 " + MAX_ROWS + " 条。请加上更具体的条件再问。"
                : "共 " + rows.size() + " 条。");
    }

    // ============================================================
    // 拼条件
    // ============================================================

    /**
     * 把校验过的筛选拼成 where 子句。
     *
     * <p>⚠️ **拼进语句的只有目录里的表达式**；用户/模型给的文字一律走
     * {@code :pN} 绑定参数。这两件事在这个方法里必须分清，
     * 混了就等于把注入面重新打开。
     */
    private String buildConditions(QueryCatalog.Metric metric, List<QueryPlan.AppliedFilter> filters,
                                   Map<String, Object> params) {
        // ⚠️ 条件先攒在局部，最后统一决定要不要加 " where " 前缀 ——
        // 一开始我是在外面直接拼 `from ... + where`，结果没筛选条件时看着正常，
        // **一加筛选就变成 `from DeviceRepair rr.reportTime >= ...`**（where 整个丢了），
        // 报一句看不懂的 JPQL 语法错。测试里"不加条件"的用例全过，把这个洞盖住了
        StringBuilder where = new StringBuilder();
        if (metric.fixedWhere() != null) {
            where.append(metric.fixedWhere());
        }

        int seq = 0;
        for (QueryPlan.AppliedFilter applied : filters) {
            QueryCatalog.Filter filter = applied.filter();
            String param = "p" + (seq++);

            switch (filter.kind()) {
                case KEYWORD -> {
                    List<String> ors = new ArrayList<>();
                    for (String expr : filter.exprs()) {
                        ors.add(expr + " like :" + param);
                    }
                    and(where, "(" + String.join(" or ", ors) + ")");
                    params.put(param, "%" + applied.value() + "%");
                }
                case ENUM -> {
                    String expr = filter.exprs().get(0);
                    if ("repairStatus".equals(filter.key())) {
                        // 工单状态有历史值（「待维修」），筛「待受理」必须同时匹配 ——
                        // 收在 DeviceRepair.expandStatusFilter 里，三处共用同一份口径
                        and(where, expr + " in :" + param);
                        params.put(param, DeviceRepair.expandStatusFilter(applied.value()));
                    } else {
                        and(where, expr + " = :" + param);
                        params.put(param, applied.value());
                    }
                }
                case TIME_RANGE -> {
                    String expr = filter.exprs().get(0);
                    LocalDateTime[] range = toRange(applied.value());
                    if (range == null) {
                        // ALL：不加条件
                        continue;
                    }
                    and(where, expr + " >= :" + param + "From and " + expr + " < :" + param + "To");
                    params.put(param + "From", range[0]);
                    params.put(param + "To", range[1]);
                }
            }
        }
        return where.length() == 0 ? "" : " where " + where;
    }

    private static void and(StringBuilder where, String condition) {
        if (where.length() > 0) {
            where.append(" and ");
        }
        where.append(condition);
    }

    /**
     * 时间范围的起止。
     *
     * <p>⚠️ 用**左闭右开**（{@code >= 起 and < 止}）而不是 {@code between}：
     * 后者在"当天"这种场景下会漏掉当天 23:59 之后的时间戳。
     * 这个项目在用户管理的日期筛选上踩过同一个坑（那次是 off-by-one 漏掉整天）。
     *
     * @return {@code [起, 止)}；{@code ALL} 返回 null 表示不加时间条件
     */
    private static LocalDateTime[] toRange(String key) {
        LocalDate today = LocalDate.now();
        return switch (key) {
            case "TODAY" -> new LocalDateTime[]{
                    today.atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "LAST_7_DAYS" -> new LocalDateTime[]{
                    today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "LAST_30_DAYS" -> new LocalDateTime[]{
                    today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "THIS_MONTH" -> new LocalDateTime[]{
                    today.withDayOfMonth(1).atStartOfDay(),
                    today.withDayOfMonth(1).plusMonths(1).atStartOfDay()};
            case "LAST_MONTH" -> new LocalDateTime[]{
                    today.withDayOfMonth(1).minusMonths(1).atStartOfDay(),
                    today.withDayOfMonth(1).atStartOfDay()};
            case "THIS_YEAR" -> new LocalDateTime[]{
                    today.withDayOfYear(1).atStartOfDay(),
                    today.withDayOfYear(1).plusYears(1).atStartOfDay()};
            default -> null;   // ALL
        };
    }

    private static void bind(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
    }

    // ============================================================
    // 取值格式化
    // ============================================================

    private String labelOf(Object value, QueryCatalog.ValueKind kind,
                           Map<Long, String> deptNames, Map<Long, String> categoryNames) {
        if (kind == QueryCatalog.ValueKind.DEPT_ID) {
            Long id = asLong(value);
            return id == null ? UNKNOWN_DEPT : deptNames.getOrDefault(id, UNKNOWN_DEPT);
        }
        if (kind == QueryCatalog.ValueKind.CATEGORY_ID) {
            Long id = asLong(value);
            return id == null ? UNKNOWN_CATEGORY : categoryNames.getOrDefault(id, UNKNOWN_CATEGORY);
        }
        return format(value);
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /**
     * 把查询结果转成给人看的字符串。
     *
     * <p>金额那类 BigDecimal 要**去掉多余的尾零**：数据库里是 {@code DECIMAL(12,2)}，
     * 直接 toString 会得到 {@code 1234.50} 甚至 {@code 180.00}，
     * 而"一共花了多少钱"这种数字后面挂两个零看着很别扭。
     * 用 {@code toPlainString} 而不是 toString —— 后者在整百的时候会输出科学计数法。
     */
    private static String format(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    // ============================================================
    // 一句话结论（模板拼的，不调模型）
    // ============================================================

    private static String summarizeAggregate(QueryCatalog.Metric metric, QueryCatalog.Field groupBy,
                                             List<List<String>> rows, boolean truncated) {
        if (!rows.isEmpty() && groupBy == null) {
            return "「" + metric.label() + "」：" + rows.get(0).get(0);
        }
        if (rows.isEmpty()) {
            return "没有符合条件的数据。";
        }
        String top = "按「" + groupBy.label() + "」统计「" + metric.label() + "」，共 "
                + rows.size() + " 组，最多的是「" + rows.get(0).get(0) + "」（" + rows.get(0).get(1) + "）。";
        return truncated ? top + "结果超过 " + MAX_ROWS + " 组，只显示了前 " + MAX_ROWS + " 组。" : top;
    }
}
