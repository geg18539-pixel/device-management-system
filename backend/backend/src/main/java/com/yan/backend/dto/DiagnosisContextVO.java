package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次故障诊断的**检索结果**（还没有模型生成的部分）。
 *
 * <p>单独把"检索到了什么"返回给前端，而不是只返回最终答案，是这一步的核心设计：
 * 运维可以直接看到模型是**依据哪些材料**说这番话的。
 * 材料本身就不对的话（比如知识库里根本没有这份手册），
 * 一眼就能看出来，而不是对着一段听起来很专业的回答猜它靠不靠谱。
 *
 * <p>这批是"接生成"之前先要暴露出来的东西，也是排查"AI 答得不对"的第一现场。
 */
public class DiagnosisContextVO {

    private String faultDesc;

    /** 有指定设备时带上它的基本信息，提示词里要用 */
    private Long deviceId;
    private String deviceName;
    private String deviceModel;

    /** 命中的知识库片段 */
    private List<KnowledgeSearchHitVO> knowledgeHits = new ArrayList<>();

    /** 相似的历史维修工单 */
    private List<SimilarCaseVO> similarCases = new ArrayList<>();

    /** 建议备件：相似工单里实际领用过的 */
    private List<SuggestedPartVO> suggestedParts = new ArrayList<>();

    /**
     * 给用户的提示。资料不足时说明为什么 ——
     * 三种情况（知识库是空的 / 没有相似工单 / 都没有）该做的事完全不同。
     */
    private String notice;

    public String getFaultDesc() {
        return faultDesc;
    }

    public void setFaultDesc(String faultDesc) {
        this.faultDesc = faultDesc;
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

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public List<KnowledgeSearchHitVO> getKnowledgeHits() {
        return knowledgeHits;
    }

    public void setKnowledgeHits(List<KnowledgeSearchHitVO> knowledgeHits) {
        this.knowledgeHits = knowledgeHits;
    }

    public List<SimilarCaseVO> getSimilarCases() {
        return similarCases;
    }

    public void setSimilarCases(List<SimilarCaseVO> similarCases) {
        this.similarCases = similarCases;
    }

    public List<SuggestedPartVO> getSuggestedParts() {
        return suggestedParts;
    }

    public void setSuggestedParts(List<SuggestedPartVO> suggestedParts) {
        this.suggestedParts = suggestedParts;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }
}
