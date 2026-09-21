package com.yan.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 知识库的文本块及其向量。
 *
 * <p>一个文档被切成若干块，每块一行。检索就是拿问题去和这些块的向量比余弦相似度，
 * 命中的块连同它所在的文档一起返回。
 *
 * <p><b>为什么存块而不是存整篇文档的向量</b>：整篇文档一个向量的话，
 * 一篇 50 页的手册会被压成一个点，里面具体哪一段讲了什么完全丢失 ——
 * 用户问"XX 型号的额定电压是多少"，检索到的会是整本手册，
 * 生成阶段还得再读一遍全文。切块之后命中就是那一段，答案直接可用。
 *
 * <p><b>为什么记 chunkIndex</b>：检索命中一块之后，可以顺手把**前后各一块**
 * 也带上作为上下文。块与块之间本来就有重叠（见切分逻辑），
 * 但重叠只有几十个字，对于"这一条讲什么"往往不够。
 *
 * <p><b>为什么同时记 dimension</b>：维度不同的向量比余弦是**没有意义**的
 * （长度不一样，根本算不了），而且换嵌入模型后新旧向量不在同一个语义空间。
 * 检索时按 (模型, 维度) 分组比较，不一致的直接跳过并给出提示，
 * 而不是硬算出一个看起来正常的错分数。
 */
@Entity
@Table(name = "knowledge_chunk", indexes = {
        @Index(name = "idx_chunk_knowledge", columnList = "knowledge_id,chunk_index")
})
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属文档 id。**用裸 id 而不是 @ManyToOne** —— 和项目里其它地方一致，避免懒加载问题 */
    @Column(name = "knowledge_id", nullable = false)
    private Long knowledgeId;

    /** 在文档里的序号，从 0 开始。用于取相邻块做上下文 */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /** 文本块内容 */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 向量。float[] 按大端序逐个写成字节。
     *
     * <p>存二进制而不是逗号分隔的文本：768 维的向量，二进制是 3072 字节，
     * 文本形式要 7000+ 字符，差一倍多。编解码收在 {@code EmbeddingCodec} 里，
     * 有往返测试兜底。
     */
    // ⚠️ 不能用 @Lob：MySQL 上会生成 tinytext/tinyblob（255 字节），详见 entity/package-info.java
    @Column(name = "embedding", nullable = false, columnDefinition = "BLOB")
    private byte[] embedding;

    /** 向量维度。和 content 里的字符数无关，是模型的输出维度 */
    @Column(name = "dimension", nullable = false)
    private Integer dimension;

    /** 算这个向量用的模型名。和 dimension 一起判断"这两个向量能不能比" */
    @Column(name = "embed_model", length = 100)
    private String embedModel;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    // ---------- getter / setter ----------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(Long knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(String embedModel) {
        this.embedModel = embedModel;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
