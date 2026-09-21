package com.yan.backend.dto;

/**
 * 关系图谱里的一个节点。
 *
 * <h3>⚠️ 这里给的是「语义」，不是「颜色」</h3>
 *
 * <p>节点带的是 {@link #tone}（ok / warn / crit / idle / primary / primarySoft），
 * 而不是具体的色值。理由是**颜色分主题**：亮色下"警告"是 {@code #8f6216}，
 * 暗色下是 {@code #d3a041}，而且 ECharts 画在 canvas 上读不到 CSS 变量
 * （见前端 {@code utils/chartColors.ts}）。后端返回色值的话，切主题时要么图不变色、
 * 要么得让后端知道当前主题 —— 两条路都不对。
 *
 * <p>所以分工是：**后端说"这是个未完结的工单"，前端按当前主题把它画成琥珀色**。
 *
 * <h3>节点的 id 必须全局唯一</h3>
 *
 * <p>ECharts 的 graph 用节点名做索引，重名的节点会被合并成一个。
 * 所以 id 一律是 {@code 类型:主键}（如 {@code repair:7}），
 * 不然"设备 1"和"工单 1"会撞在一起。
 */
public class GraphNodeVO {

    /** 全局唯一：{@code 类型:主键} */
    private String id;

    /** 节点上显示的文字 */
    private String name;

    /**
     * 节点类型：device / dept / category / repair / maintenance / part。
     *
     * <p>前端据此决定**画什么形状** —— 形状管类别、颜色管语义，
     * 这样六种节点只用几种颜色也不会混淆。
     */
    private String nodeType;

    /** 类型的中文名（在图例和悬停提示里用）。后端给，免得前端再维护一份 */
    private String typeLabel;

    /** 第二行小字：状态、编号、库存之类的补充信息。可为空 */
    private String subtitle;

    /** 语义色：ok / warn / crit / idle / primary / primarySoft */
    private String tone;

    /** 是否中心节点（当前正在看的这台设备）。前端会把它画得更大 */
    private boolean center;

    /**
     * 点击节点后跳到哪。null 表示这个节点不可点。
     *
     * <p>用"类型 + id"而不是直接给 URL：路由是前端的事，
     * 后端拼路径等于把前端的路由结构复制一份，改一边忘一边就会出现死链。
     */
    private String routeType;

    private Long routeId;

    // ---------- getter / setter ----------

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public void setTypeLabel(String typeLabel) {
        this.typeLabel = typeLabel;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public boolean isCenter() {
        return center;
    }

    public void setCenter(boolean center) {
        this.center = center;
    }

    public String getRouteType() {
        return routeType;
    }

    public void setRouteType(String routeType) {
        this.routeType = routeType;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }
}
