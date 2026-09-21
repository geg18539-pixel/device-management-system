package com.yan.backend.ai.query;

import java.util.List;

/**
 * 一次问数的**解析结果** —— 从模型的输出里校验之后得到的东西。
 *
 * <p>它的每个字段都保证**来自 {@link QueryCatalog}**：
 * {@code metric} 是目录里的条目，{@code groupBy} 是该指标声明过的维度，
 * 每个筛选的取值都过了校验。到了 {@link QueryExecutor} 那一层，
 * 不再需要做任何"这个字符串安不安全"的判断 —— 因为已经不可能不安全了。
 *
 * <p>这是"受控问数"和"自由写 SQL"最本质的区别：**校验发生在一处、而且是一次性的**，
 * 而不是在拼 SQL 的时候到处补判断。
 *
 * @param understanding 用一句中文复述"我理解你在问什么"。
 *                      <b>必须返回给用户看</b> —— 小模型一定会有理解错的时候，
 *                      让用户一眼看出"它把我的问题理解成什么了"，
 *                      比让他对着一个莫名其妙的结果猜要强得多。
 */
public record QueryPlan(QueryCatalog.Metric metric,
                        QueryCatalog.Field groupBy,
                        List<AppliedFilter> filters,
                        String understanding) {

    /** 一个已经校验通过的筛选条件 */
    public record AppliedFilter(QueryCatalog.Filter filter, String value) {}
}
