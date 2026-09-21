package com.yan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 故障诊断的配置（对应 application.yml 里的 app.diagnosis.*） */
@Component
@ConfigurationProperties(prefix = "app.diagnosis")
public class DiagnosisProperties {

    /** 检索知识库时取前几条片段 */
    private int knowledgeTopK = 4;

    /** 返回几条相似历史工单 */
    private int similarCaseTopK = 3;

    /**
     * 相似案例只在这个天数窗口里找。
     *
     * <p>不设窗口的话，五年前那台早就报废的同类设备会一直冒出来，
     * 而它的维修经验对现在的设备未必适用（型号换代、工艺改了）。
     */
    private int similarCaseWindowDays = 1095;

    /**
     * 一次最多取多少条候选来打分。
     *
     * <p>打分是在内存里逐条算的，候选不设上限的话，
     * 工单表积累几万条之后每次诊断都要遍历全表。
     */
    private int similarCaseCandidateLimit = 500;

    /**
     * 相似案例的最低匹配度。
     *
     * <p>0.3 是刻意留低的：**"同一台设备以前出过的问题"即使文字完全不重合，
     * 也有参考价值**（同一台设备的故障往往有关联）。所以"同设备"这个加成
     * （0.35）本身就单独过线，能带着一条只靠元数据匹配的案例进来。
     */
    private double similarCaseMinScore = 0.3;

    public int getKnowledgeTopK() {
        return knowledgeTopK;
    }

    public void setKnowledgeTopK(int knowledgeTopK) {
        this.knowledgeTopK = knowledgeTopK;
    }

    public int getSimilarCaseTopK() {
        return similarCaseTopK;
    }

    public void setSimilarCaseTopK(int similarCaseTopK) {
        this.similarCaseTopK = similarCaseTopK;
    }

    public int getSimilarCaseWindowDays() {
        return similarCaseWindowDays;
    }

    public void setSimilarCaseWindowDays(int similarCaseWindowDays) {
        this.similarCaseWindowDays = similarCaseWindowDays;
    }

    public int getSimilarCaseCandidateLimit() {
        return similarCaseCandidateLimit;
    }

    public void setSimilarCaseCandidateLimit(int similarCaseCandidateLimit) {
        this.similarCaseCandidateLimit = similarCaseCandidateLimit;
    }

    public double getSimilarCaseMinScore() {
        return similarCaseMinScore;
    }

    public void setSimilarCaseMinScore(double similarCaseMinScore) {
        this.similarCaseMinScore = similarCaseMinScore;
    }
}
