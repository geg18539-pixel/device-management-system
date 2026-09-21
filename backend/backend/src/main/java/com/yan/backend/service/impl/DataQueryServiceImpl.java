package com.yan.backend.service.impl;

import com.yan.backend.ai.query.QueryCatalog;
import com.yan.backend.ai.query.QueryExecutor;
import com.yan.backend.ai.query.QueryPlan;
import com.yan.backend.ai.query.QueryPlanner;
import com.yan.backend.dto.QueryCatalogVO;
import com.yan.backend.dto.QueryResultVO;
import com.yan.backend.service.DataQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 问数的编排：先让模型把问题翻译成结构化查询，再按目录去查库。
 *
 * <p>这一层只有三行实质代码，看着像是可以省掉、让 Controller 直接调
 * {@link QueryPlanner} 和 {@link QueryExecutor}。留着它的理由和项目里其它
 * Service 一样：**策略放这里，Controller 只管 HTTP**。
 * 以后要加"问数也记一条操作日志""同一句话短时间内的结果缓存一下"，
 * 都落在这一层，不用回头改 Controller。
 */
@Service
public class DataQueryServiceImpl implements DataQueryService {

    private final QueryPlanner planner;
    private final QueryExecutor executor;
    private final QueryCatalog catalog;

    public DataQueryServiceImpl(QueryPlanner planner,
                                QueryExecutor executor,
                                QueryCatalog catalog) {
        this.planner = planner;
        this.executor = executor;
        this.catalog = catalog;
    }

    @Override
    public QueryResultVO ask(String question) {
        QueryPlan plan = planner.plan(question);
        return executor.execute(question.strip(), plan);
    }

    @Override
    public QueryCatalogVO catalog() {
        QueryCatalogVO vo = new QueryCatalogVO();
        List<QueryCatalogVO.MetricInfo> metrics = new ArrayList<>();
        for (QueryCatalog.Metric m : catalog.allMetrics()) {
            metrics.add(new QueryCatalogVO.MetricInfo(
                    m.label(), m.hint(), m.mode() == QueryCatalog.Mode.LIST));
        }
        vo.setMetrics(metrics);
        vo.setExamples(catalog.exampleQuestions());
        return vo;
    }
}
