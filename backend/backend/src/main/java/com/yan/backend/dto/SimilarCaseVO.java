package com.yan.backend.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条相似的历史维修工单。
 *
 * <p>只有**走完流程、且写了维修结果**的工单才会进候选 ——
 * 待受理 / 维修中的工单还没有结论，拿它当"案例"是误导。
 */
public class SimilarCaseVO {

    private Long repairId;
    private Long deviceId;
    private String deviceName;
    private String faultDesc;
    private String faultType;
    /** 当时的维修结果。这是"案例"最有价值的部分 */
    private String repairResult;
    private String repairer;
    private LocalDateTime finishTime;

    /**
     * 匹配度，0~1。
     *
     * <p>⚠️ 这是**词面重合度 + 元数据加权**算出来的，**不是语义相似度** ——
     * 界面上要如实写成「匹配度」而不是「语义相似度」。
     * 不引语义检索的原因见 {@code SimilarCaseFinder} 的类注释。
     */
    private double similarity;

    /** 为什么算相似（「同一台设备」「同类故障」「描述高度重合」），按贡献从大到小 */
    private List<String> matchReasons = new ArrayList<>();

    public Long getRepairId() {
        return repairId;
    }

    public void setRepairId(Long repairId) {
        this.repairId = repairId;
    }

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

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
    }

    public String getFaultType() {
        return faultType;
    }

    public void setFaultType(String faultType) {
        this.faultType = faultType;
    }

    public String getRepairResult() {
        return repairResult;
    }

    public void setRepairResult(String repairResult) {
        this.repairResult = repairResult;
    }

    public String getRepairer() {
        return repairer;
    }

    public void setRepairer(String repairer) {
        this.repairer = repairer;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }

    public List<String> getMatchReasons() {
        return matchReasons;
    }

    public void setMatchReasons(List<String> matchReasons) {
        this.matchReasons = matchReasons;
    }
}
