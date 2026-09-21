package com.yan.backend.service;

import com.yan.backend.dto.QueryCatalogVO;
import com.yan.backend.dto.QueryResultVO;

/**
 * 自然语言问数。
 *
 * <p><b>它和 AI 助手、故障诊断的区别</b>：那两个是"模型负责说话"，
 * 结果由模型生成、可能不准；这个是"模型只负责听懂"，
 * 真正的数据和数字全部来自数据库。所以这里的结果**是可复现、可核对的** ——
 * 同一句话问两次拿到的一定是同一个数。
 *
 * <p>返回的数据范围只限设备、维修工单、配件。**不含用户、角色、菜单**：
 * 那几张表有 {@code @RequireRole("admin")} 拦着，如果问数能查到，
 * 普通操作员问一句"系统里有哪些用户"就等于绕过了那道防线。
 * 口径和 AI 数据快照、AI 工具完全一致。
 */
public interface DataQueryService {

    /**
     * 回答一个问题。
     *
     * @throws IllegalArgumentException 问题没被理解、或者模型选的东西不在目录里。
     *         消息是给用户看的，前端直接展示
     */
    QueryResultVO ask(String question);

    /** 能问什么：指标清单 + 示例问题 */
    QueryCatalogVO catalog();
}
