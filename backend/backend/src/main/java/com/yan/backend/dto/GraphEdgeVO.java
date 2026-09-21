package com.yan.backend.dto;

/**
 * 关系图谱里的一条边。
 *
 * <p>{@link #source} / {@link #target} 是 {@link GraphNodeVO#getId()}，
 * 不是主键、也不是显示名 —— 用 id 才不会因为"两台设备重名"而连错。
 *
 * <p>边**没有方向语义**（同一个部门下所有设备都连着它，谁指向谁不重要），
 * 所以前端不加箭头，只在悬停时显示 {@link #label}。
 */
public class GraphEdgeVO {

    private String source;

    private String target;

    /** 关系名：所属部门 / 分类 / 报修 / 保养计划 / 领用 */
    private String label;

    public GraphEdgeVO() {
    }

    public GraphEdgeVO(String source, String target, String label) {
        this.source = source;
        this.target = target;
        this.label = label;
    }

    // ---------- getter / setter ----------

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
