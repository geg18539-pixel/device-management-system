package com.yan.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库配置（对应 application.yml 里的 app.knowledge.*）。
 *
 * <p>和附件配置一样抽成配置类：切分大小、重叠长度、检索条数这几个值
 * 彼此有关联（重叠必须小于块大小，否则会无限循环），放在一处才好一起看。
 */
@Component
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeProperties {

    /**
     * 允许上传并解析的扩展名。
     *
     * <p>⚠️ 这是**在附件白名单之上再加的一层**，比它更窄。
     * 附件允许图片和 Office（存下来给人下载就行），但知识库要能把文件
     * **读成文本**才有意义 —— 收一张 png 进来只会在解析那一步失败。
     * 两层各有分工：附件白名单管"能不能安全地存在服务器上"，
     * 这一层管"能不能被解析成知识"。
     */
    private List<String> allowedExtensions = List.of("pdf", "txt", "md");

    /** 每个文本块的目标字符数。中文按字符算，500 字大概是 300 多个 token */
    private int chunkSize = 500;

    /**
     * 相邻块之间的重叠字符数。
     *
     * <p>必须有重叠：一段话被硬切在两块之间时，切点两侧各自都读不通，
     * 检索命中哪一边都拿不到完整意思。重叠一段之后，切口附近的信息
     * 至少有一块是完整的。
     *
     * <p>代价是同一段文字会被嵌入两次、占两份空间。几十个字的量级
     * 换来"切点不丢信息"，很划算。
     */
    private int chunkOverlap = 80;

    /** 单个文档最多切多少块。防止几百兆的扫描件把库撑爆 */
    private int maxChunksPerDoc = 3000;

    /** 检索默认返回几条 */
    private int topK = 5;

    /**
     * 相似度的最低门槛，低于它的命中直接丢掉。
     *
     * <p>没有这个门槛的话，问一个知识库里根本没写过的问题，
     * 也会返回 topK 条"最不相关"的片段，而且看起来像是找到了。
     * 有门槛才能诚实地回答"没找到相关内容"。
     */
    private double minScore = 0.35;

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public int getMaxChunksPerDoc() {
        return maxChunksPerDoc;
    }

    public void setMaxChunksPerDoc(int maxChunksPerDoc) {
        this.maxChunksPerDoc = maxChunksPerDoc;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }
}
