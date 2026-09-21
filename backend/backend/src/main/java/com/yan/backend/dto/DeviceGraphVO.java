package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 以某台设备为中心的关系图谱。
 *
 * <h3>为什么是「以设备为中心」而不是全库总览</h3>
 *
 * <p>全库关系图在真实数据下**一定糊成一团**：几百台设备连着几十个部门、
 * 上千张工单，力导向布局出来的是一坨分不清的毛线球，既看不出结构也点不中节点。
 * 而以一台设备为中心、只展开它的直接关系，节点数是**可控的**
 * （1 台设备 + 1 个部门 + 1 个分类 + 若干工单 + 若干配件），
 * 图形上永远读得清。
 *
 * <p>"继续往下看"靠的是**点另一个设备节点重新以它为中心** ——
 * 人沿着关系走，而不是让程序一次性画出来。
 */
public class DeviceGraphVO {

    private Long deviceId;

    private String deviceName;

    private List<GraphNodeVO> nodes = new ArrayList<>();

    private List<GraphEdgeVO> edges = new ArrayList<>();

    /** 这台设备**一共**有多少张维修工单（不受展示条数限制） */
    private int repairTotal;

    /** 图上实际画了几张工单 */
    private int repairShown;

    /** 这台设备**一共**关联过多少种配件 */
    private int partTotal;

    /** 图上实际画了几种配件 */
    private int partShown;

    /** 同部门**一共**还有多少台别的设备 */
    private int siblingTotal;

    /** 图上实际画了几台同部门设备 */
    private int siblingShown;

    /**
     * 被截断时给用户看的一句话。
     *
     * <p><b>必须写出来</b>：图上看不到的东西，用户默认不会以为"是没显示"，
     * 而会以为"就是没有"。不说明的话，"这台设备只修过 5 次"这个错误印象
     * 会直接影响判断。空串表示没有截断。
     */
    private String note = "";

    // ---------- getter / setter ----------

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public List<GraphNodeVO> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNodeVO> nodes) {
        this.nodes = nodes;
    }

    public List<GraphEdgeVO> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdgeVO> edges) {
        this.edges = edges;
    }

    public int getRepairTotal() {
        return repairTotal;
    }

    public void setRepairTotal(int repairTotal) {
        this.repairTotal = repairTotal;
    }

    public int getRepairShown() {
        return repairShown;
    }

    public void setRepairShown(int repairShown) {
        this.repairShown = repairShown;
    }

    public int getPartTotal() {
        return partTotal;
    }

    public void setPartTotal(int partTotal) {
        this.partTotal = partTotal;
    }

    public int getPartShown() {
        return partShown;
    }

    public void setPartShown(int partShown) {
        this.partShown = partShown;
    }

    public int getSiblingTotal() {
        return siblingTotal;
    }

    public void setSiblingTotal(int siblingTotal) {
        this.siblingTotal = siblingTotal;
    }

    public int getSiblingShown() {
        return siblingShown;
    }

    public void setSiblingShown(int siblingShown) {
        this.siblingShown = siblingShown;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
