package com.yan.backend.dto;

/** 一条命中的知识片段 */
public class KnowledgeSearchHitVO {

    private Long chunkId;
    private Long knowledgeId;
    /** 所属文档标题。命中之后最要紧的就是"这是从哪份文件里来的" */
    private String docTitle;

    /** 在文档里的序号。前端可以显示成「第 N 段」，方便回去翻原文 */
    private int chunkIndex;

    /** 命中的原文 */
    private String content;

    /** 余弦相似度。范围通常是 [0, 1]，越大越相关 */
    private double score;

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
