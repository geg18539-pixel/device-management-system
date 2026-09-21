package com.yan.backend.ai.query;

import com.yan.backend.ai.StructuredJson;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.ChatMessage;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.ai.provider.StructuredRequest;
import com.yan.backend.service.AiSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一句中文问句翻译成一次结构化查询。
 *
 * <h3>模型在这里只做一件事：从目录里挑</h3>
 *
 * <p>它输出的每个值都必须能在 {@link QueryCatalog} 里找到，
 * 找不到就整条拒掉、给用户一句可操作的提示。**所以这个类的主要工作量不在提示词，
 * 而在校验和"校验不过时说什么"** —— 小模型选错是常态，
 * 关键是让它选错时的表现是一句人话，而不是一个莫名其妙的结果或者 500。
 *
 * <h3>提示词里必须把整个目录铺开</h3>
 *
 * <p>目录只有五项、几十行，全部塞进提示词完全放得下。这么做是因为
 * **让模型"挑"比让它"想"可靠得多**：不铺开的话它会自己发明指标名和取值，
 * 然后全部被校验拒掉。
 */
@Component
public class QueryPlanner {

    private static final Logger log = LoggerFactory.getLogger(QueryPlanner.class);

    /** 问句长度上限。不是安全边界，是防呆 —— 真贴一整篇文章进来也是这个上限 */
    private static final int MAX_QUESTION_LENGTH = 200;

    /** 规划用的温度。要的是稳定复现，不要创意 */
    private static final double TEMPERATURE = 0.1;

    /** 规划的 JSON 只有几十个字，300 足够了 */
    private static final int MAX_TOKENS = 300;

    /** 不分组时 groupBy 的取值 */
    private static final String NO_GROUP = "";

    private static final String SYSTEM_PROMPT = """
            你是设备管理系统的数据查询助手。用户会用中文问一个关于设备、维修工单或配件的问题，
            你要把它翻译成一次结构化的查询。

            只输出一个 JSON 对象，包含四个字段：
            - metric：用哪个指标，必须从下面列出的 key 里原样选一个
            - groupBy：按什么分组。不需要分组时填空字符串 ""
            - filters：筛选条件。键必须是该指标支持的筛选字段，取值必须从列出的可填值里选
            - understanding：用一句中文复述"我理解你在问什么"，给用户核对用

            规则：
            1. 只能选下面列出的 key 和取值，**不要发明任何新的**。
            2. 用户问「哪些…」「列出来」这类要明细的，选标注为【清单】的指标；
               问「多少」「几个」「合计」这类要数字的，选标注为【统计】的指标。
            3. 拿不准选哪个的时候，选最接近的那个，并在 understanding 里如实说明。
            4. 不要输出任何解释、Markdown 代码块或额外文字，只要那个 JSON 对象。
            """;

    private final AiProviderRegistry providerRegistry;
    private final AiSettingsService settings;
    private final StructuredJson structuredJson;
    private final QueryCatalog catalog;

    public QueryPlanner(AiProviderRegistry providerRegistry,
                        AiSettingsService settings,
                        StructuredJson structuredJson,
                        QueryCatalog catalog) {
        this.providerRegistry = providerRegistry;
        this.settings = settings;
        this.structuredJson = structuredJson;
        this.catalog = catalog;
    }

    /**
     * 解析一次问句。
     *
     * @throws IllegalArgumentException 模型选的东西不在目录里，或者组合不合法。
     *         消息是**直接给用户看的**，所以必须写成人话并说明"那能问什么"。
     *         走 {@code GlobalExceptionHandler} 已有的 400 分支，不会变成 500
     */
    public QueryPlan plan(String question) {
        String text = question == null ? "" : question.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("请先描述你想查什么，例如「各部门有多少台设备」");
        }
        if (text.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("问题太长了，请控制在 "
                    + MAX_QUESTION_LENGTH + " 个字以内");
        }

        LlmProvider provider = providerRegistry.currentLlm();
        StructuredRequest request = new StructuredRequest(
                null,
                List.of(
                        ChatMessage.system(SYSTEM_PROMPT),
                        ChatMessage.user(buildCatalogPrompt(text))),
                buildJsonSchema(),
                TEMPERATURE,
                MAX_TOKENS,
                // ⚠️ 和故障分析同理：固定关思考。输出只有几十个字的 JSON，
                // 思考会把 300 的预算花在中间过程上，最后交不出结果
                Boolean.FALSE);

        String content = provider.structured(request);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("模型没有返回结果，请换个说法再试一次");
        }
        return validate(structuredJson.extract(content));
    }

    // ============================================================
    // 提示词里铺开目录
    // ============================================================

    private String buildCatalogPrompt(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("可以使用的指标如下（只能从这里选）：\n\n");

        for (QueryCatalog.Metric m : catalog.allMetrics()) {
            sb.append("### ").append(m.label())
                    .append("（key = ").append(m.key()).append("）")
                    .append(m.mode() == QueryCatalog.Mode.LIST ? "【清单】" : "【统计】")
                    .append("\n");
            sb.append(m.hint()).append("\n");

            if (m.mode() == QueryCatalog.Mode.AGGREGATE) {
                sb.append("可分组维度：");
                sb.append(m.dimensions().isEmpty() ? "（不支持分组）"
                        : joinFields(m.dimensions()));
                sb.append("\n");
            }

            sb.append("可筛选字段：");
            sb.append(m.filters().isEmpty() ? "（不支持筛选）" : "\n");
            for (QueryCatalog.Filter f : m.filters()) {
                sb.append("  - ").append(f.key()).append("（").append(f.label()).append("）：");
                switch (f.kind()) {
                    case KEYWORD -> sb.append("填要搜索的文字");
                    case ENUM -> sb.append("只能填 ").append(joinOptions(f.options())).append(" 之一");
                    case TIME_RANGE -> sb.append("只能填 ").append(joinTimeRanges()).append(" 之一");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        sb.append("用户的问题是：").append(question);
        return sb.toString();
    }

    private static String joinFields(List<QueryCatalog.Field> fields) {
        List<String> parts = new ArrayList<>();
        for (QueryCatalog.Field f : fields) {
            parts.add(f.key() + "（" + f.label() + "）");
        }
        return String.join("、", parts);
    }

    private static String joinOptions(List<QueryCatalog.Option> options) {
        List<String> parts = new ArrayList<>();
        for (QueryCatalog.Option o : options) {
            parts.add(o.value());
        }
        return String.join(" / ", parts);
    }

    private static String joinTimeRanges() {
        List<String> parts = new ArrayList<>();
        for (QueryCatalog.TimeRange t : QueryCatalog.TIME_RANGES) {
            parts.add(t.key() + "（" + t.label() + "）");
        }
        return String.join(" / ", parts);
    }

    // ============================================================
    // JSON Schema
    // ============================================================

    private Map<String, Object> buildJsonSchema() {
        // 指标 key 做成 enum：这是最有效的一道约束 ——
        // 支持约束的提供方（原生 Ollama）会让模型根本吐不出目录外的值
        List<String> metricKeys = catalog.allMetrics().stream()
                .map(QueryCatalog.Metric::key).toList();

        // 维度 key 取所有指标的并集。**这里只能给并集** ——
        // JSON Schema 表达不了"哪个指标配哪些维度"这种条件约束，
        // 所以"设备数量不能按工单状态分组"这种判断只能留在 validate 里做
        Set<String> dimKeys = new LinkedHashSet<>();
        dimKeys.add(NO_GROUP);
        for (QueryCatalog.Metric m : catalog.allMetrics()) {
            if (m.dimensions() != null) {
                m.dimensions().forEach(d -> dimKeys.add(d.key()));
            }
        }

        // 筛选字段同理，给并集；取值不放进 enum，留给 validate 逐个字段判断
        Map<String, Object> filterProps = new LinkedHashMap<>();
        Set<String> filterKeys = new LinkedHashSet<>();
        for (QueryCatalog.Metric m : catalog.allMetrics()) {
            if (m.filters() != null) {
                m.filters().forEach(f -> filterKeys.add(f.key()));
            }
        }
        for (String key : filterKeys) {
            filterProps.put(key, Map.of("type", "string", "description", "该筛选字段的取值"));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("metric", Map.of("type", "string", "enum", metricKeys));
        properties.put("groupBy", Map.of(
                "type", "string",
                "enum", List.copyOf(dimKeys),
                "description", "不需要分组时填空字符串"));
        properties.put("filters", Map.of(
                "type", "object",
                "properties", filterProps,
                "description", "只填用得上的字段，用不上的不要出现"));
        properties.put("understanding", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("metric", "understanding"));
        return schema;
    }

    // ============================================================
    // 校验：这里才是这个类的重点
    // ============================================================

    private QueryPlan validate(JsonNode parsed) {
        String metricKey = parsed.path("metric").asString();
        QueryCatalog.Metric metric = catalog.find(metricKey);
        if (metric == null) {
            throw new IllegalArgumentException("没理解你的问题。" + describeAvailableMetrics());
        }

        QueryCatalog.Field groupBy = resolveGroupBy(metric, parsed.path("groupBy").asString());
        List<QueryPlan.AppliedFilter> filters = resolveFilters(metric, parsed.path("filters"));

        String understanding = parsed.path("understanding").asString();
        if (understanding == null || understanding.isBlank()) {
            // 复述缺失不算致命 —— 但前端要有东西显示，用目录里的标签兜一个
            understanding = metric.label();
        }

        log.info("问数解析：指标={}，分组={}，筛选={}，复述={}",
                metric.key(), groupBy == null ? "无" : groupBy.key(),
                filters.size(), understanding);

        return new QueryPlan(metric, groupBy, filters, understanding.strip());
    }

    private QueryCatalog.Field resolveGroupBy(QueryCatalog.Metric metric, String key) {
        String raw = key == null ? "" : key.strip();
        if (raw.isEmpty()) {
            return null;
        }
        // 清单形态不分组：模型多填了就当没填，不值得为此拒掉整个问题
        if (metric.mode() != QueryCatalog.Mode.AGGREGATE || metric.dimensions() == null) {
            return null;
        }
        for (QueryCatalog.Field d : metric.dimensions()) {
            if (d.key().equals(raw)) {
                return d;
            }
        }
        throw new IllegalArgumentException("「" + metric.label() + "」不支持按「" + raw + "」分组。"
                + "它支持的维度是：" + (metric.dimensions().isEmpty() ? "无（这个指标本身就是个数）"
                : joinFields(metric.dimensions())));
    }

    private List<QueryPlan.AppliedFilter> resolveFilters(QueryCatalog.Metric metric, JsonNode filters) {
        List<QueryPlan.AppliedFilter> out = new ArrayList<>();
        if (filters == null || !filters.isObject() || metric.filters() == null) {
            return out;
        }

        var names = filters.propertyNames();
        for (String key : names) {
            String raw = filters.path(key).asString();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            QueryCatalog.Filter filter = findFilter(metric, key);
            if (filter == null) {
                throw new IllegalArgumentException("「" + metric.label() + "」不支持按「" + key + "」筛选。"
                        + "它支持的筛选是：" + (metric.filters().isEmpty() ? "无"
                        : metric.filters().stream().map(QueryCatalog.Filter::label).toList()));
            }
            out.add(new QueryPlan.AppliedFilter(filter, validateFilterValue(filter, raw.strip())));
        }
        return out;
    }

    private QueryCatalog.Filter findFilter(QueryCatalog.Metric metric, String key) {
        for (QueryCatalog.Filter f : metric.filters()) {
            if (f.key().equals(key)) {
                return f;
            }
        }
        return null;
    }

    /** 校验一个筛选取值，顺便把"人写的"归一成"库里存的" */
    private String validateFilterValue(QueryCatalog.Filter filter, String raw) {
        switch (filter.kind()) {
            case KEYWORD -> {
                // 关键词是唯一一个允许自由文本的取值 —— 而且它只会作为**绑定参数**出现，
                // 不进任何 SQL 片段，所以没有注入面
                return raw.length() > 50 ? raw.substring(0, 50) : raw;
            }
            case ENUM -> {
                for (QueryCatalog.Option o : filter.options()) {
                    if (o.value().equals(raw) || o.label().equalsIgnoreCase(raw)) {
                        return o.value();
                    }
                }
                throw new IllegalArgumentException("「" + filter.label() + "」只能填："
                        + joinOptions(filter.options()) + "，不接受「" + raw + "」");
            }
            case TIME_RANGE -> {
                for (QueryCatalog.TimeRange t : QueryCatalog.TIME_RANGES) {
                    if (t.key().equalsIgnoreCase(raw) || t.label().equals(raw)) {
                        return t.key();
                    }
                }
                throw new IllegalArgumentException("时间范围只能填："
                        + joinTimeRanges() + "，不接受「" + raw + "」");
            }
            default -> throw new IllegalArgumentException("不支持的筛选类型");
        }
    }

    private String describeAvailableMetrics() {
        List<String> parts = new ArrayList<>();
        for (QueryCatalog.Metric m : catalog.allMetrics()) {
            parts.add(m.label() + "（" + m.hint() + "）");
        }
        return "我能回答的是这些：" + String.join("；", parts)
                + "。换个说法再试一次，或者点下面的示例问题。";
    }
}
