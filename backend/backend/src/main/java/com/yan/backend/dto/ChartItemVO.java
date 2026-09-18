package com.yan.backend.dto;

/**
 * 图表用的「名称 - 数值」一项。
 *
 * <p>字段名直接用 name / value，是因为 ECharts 的饼图和柱状图默认读的就是这两个字段，
 * 前端拿到可以直接塞进 series.data，不用再做一层映射。
 */
public class ChartItemVO {

    private String name;
    private long value;

    public ChartItemVO() {
    }

    public ChartItemVO(String name, long value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getValue() {
        return value;
    }

    public void setValue(long value) {
        this.value = value;
    }
}
