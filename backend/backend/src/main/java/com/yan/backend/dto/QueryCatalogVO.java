package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 「能问什么」的说明，给前端渲染成一个提示区。
 *
 * <p>返回给前端而不是写死在前端：目录在后面加了指标，这里会自动跟着变，
 * 不会出现"页面上的说明比实际能力少一条"这种事。
 */
public class QueryCatalogVO {

    /** 每个指标一行 */
    public record MetricInfo(String label, String hint, boolean listMode) {}

    private List<MetricInfo> metrics = new ArrayList<>();

    /** 示例问题，直接渲染成可点的提示 */
    private List<String> examples = new ArrayList<>();

    public List<MetricInfo> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<MetricInfo> metrics) {
        this.metrics = metrics;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }
}
