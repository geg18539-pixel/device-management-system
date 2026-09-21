package com.yan.backend.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库检索的结果。
 *
 * <p><b>这一步刻意只返回命中的片段，不返回模型生成的答案。</b>
 * 检索质量是生成质量的前提：如果检索出来的片段本身就是错的，
 * 后面接多好的模型都只是在错误材料上编。先把"检索得准不准"单独暴露出来
 * 给人看，再接生成 —— 不然两个环节都有问题时会互相掩盖，
 * 表现为"AI 答得不对"，根本不知道该修哪一边。
 */
public class KnowledgeSearchResultVO {

    /** 用户的问题原文 */
    private String query;

    /** 问题向量是用哪个模型、多少维算出来的。排查"换过模型吗"时用得上 */
    private String embedModel;
    private Integer dimension;

    /** 命中的片段，按相似度从高到低 */
    private List<KnowledgeSearchHitVO> hits = new ArrayList<>();

    /** 因为嵌入模型/维度不一致而被跳过的块数 */
    private int skippedMismatch;
    /** 索引里总共有多少块 */
    private int totalIndexed;

    /**
     * 最相近的一条得了几分，**不管有没有过门槛**。
     *
     * <p>用来区分"门槛调高了"和"确实没有相关内容" ——
     * 这两种情况的处理动作完全相反，而门槛是个静态配置，
     * 调错了不会报错，只会静默地什么都返回不了。
     */
    private double bestScore;

    /**
     * 给前端的提示。正常情况下为 null。
     *
     * <p>存在的意义：库里明明有块却一块都没命中时，必须说清是
     * "真的没有相关内容"还是"这些块的向量对不上"——
     * 前者该去补文档，后者该去重新入库，动作完全不同。
     */
    private String notice;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(String embedModel) {
        this.embedModel = embedModel;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public List<KnowledgeSearchHitVO> getHits() {
        return hits;
    }

    public void setHits(List<KnowledgeSearchHitVO> hits) {
        this.hits = hits;
    }

    public int getSkippedMismatch() {
        return skippedMismatch;
    }

    public void setSkippedMismatch(int skippedMismatch) {
        this.skippedMismatch = skippedMismatch;
    }

    public int getTotalIndexed() {
        return totalIndexed;
    }

    public void setTotalIndexed(int totalIndexed) {
        this.totalIndexed = totalIndexed;
    }

    public double getBestScore() {
        return bestScore;
    }

    public void setBestScore(double bestScore) {
        this.bestScore = bestScore;
    }

    public String getNotice() {
        return notice;
    }

    public void setNotice(String notice) {
        this.notice = notice;
    }
}
