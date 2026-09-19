package com.yan.backend.service;

public interface AiFaultAnalysisService {

    /**
     * 异步分析工单的故障描述，结果写回工单。
     *
     * <p>由事件监听触发，不要在业务代码里直接同步调用 —— 一次分析要十几秒，
     * 会拖死请求线程。
     */
    void analyzeAsync(Long repairId);
}
