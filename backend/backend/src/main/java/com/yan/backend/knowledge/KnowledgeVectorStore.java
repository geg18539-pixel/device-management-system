package com.yan.backend.knowledge;

import com.yan.backend.entity.KnowledgeChunk;
import com.yan.backend.repository.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 向量检索。
 *
 * <p><b>为什么是全量放内存 + 暴力算余弦，而不是向量数据库</b>：
 * 演示规模（几千个文本块）下，一次全表读取是几 MB、一次全量比对是几毫秒。
 * 引一个向量数据库意味着多一个要部署、要连、要同步的服务，
 * 而它解决的问题（百万级向量的近邻搜索）在这个场景下根本不存在。
 * 真上量了再换，接口就 {@link #search} 一个方法，换实现不影响任何调用方。
 *
 * <p>缓存在**进程内**、文档增删后失效。失效走的是项目里已经用过两次的
 * {@code evictAfterCommit}（见 PermissionService / SysConfigService）：
 * 在事务里直接清的话，紧接着的另一个请求会把**旧数据**读回缓存，
 * 表现为"刚传完文档却搜不到，过一会儿才好"。
 */
@Component
public class KnowledgeVectorStore {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeVectorStore.class);

    private final KnowledgeChunkRepository chunkRepository;

    /**
     * 缓存。null 表示"还没加载"。整体替换而不是原地改，
     * 所以加 volatile 就够了 —— 读者拿到的要么是旧的完整列表，要么是新的完整列表。
     */
    private volatile List<IndexedChunk> index;

    public KnowledgeVectorStore(KnowledgeChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    // ============================================================
    // 检索
    // ============================================================

    /**
     * 找和 query 最相似的若干块。
     *
     * @param queryModel 问题的向量是哪个模型算的
     * @param queryDim   问题的向量维度
     * @param minScore   相似度门槛，低于它的直接丢弃
     */
    public SearchOutcome search(float[] query, String queryModel, int queryDim,
                                int topK, double minScore) {
        List<IndexedChunk> all = index();
        List<ChunkMatch> matches = new ArrayList<>();
        int skipped = 0;
        double bestScore = 0;

        for (IndexedChunk chunk : all) {
            // ⚠️ 只比同一个模型、同一个维度的向量。
            // 换过嵌入模型之后，旧向量和新向量不在同一个语义空间，
            // 硬比会得到"看着正常、其实毫无意义"的分数，而且不报错。
            // 维度不同的更直接 —— 长度都不一样，根本算不了
            if (!chunk.model().equals(queryModel) || chunk.dimension() != queryDim) {
                skipped++;
                continue;
            }
            double score = EmbeddingCodec.cosine(query, chunk.vector());
            if (score > bestScore) {
                bestScore = score;
            }
            if (score >= minScore) {
                matches.add(new ChunkMatch(chunk.chunkId(), chunk.knowledgeId(),
                        chunk.chunkIndex(), chunk.content(), score));
            }
        }

        matches.sort(Comparator.comparingDouble(ChunkMatch::score).reversed());
        if (matches.size() > topK) {
            matches = matches.subList(0, topK);
        }
        if (skipped > 0) {
            log.warn("检索时跳过了 {} 个向量：它们是用别的嵌入模型或别的维度生成的。"
                    + "换嵌入模型之后需要把这些文档重新入库", skipped);
        }
        return new SearchOutcome(matches, skipped, all.size(), bestScore);
    }

    /** 索引里有多少块。用于页面展示和"到底有没有数据"的判断 */
    public int size() {
        return index().size();
    }

    /** 清掉缓存。下次检索时重新加载 */
    public void invalidate() {
        index = null;
    }

    /**
     * 事务提交之后再清缓存。
     *
     * <p>在事务里直接清的话，同一个事务里紧接着的读取会**读到还没提交的数据**
     * 并把它缓存起来；万一事务后来回滚了，缓存里就留下了一批根本不存在的块。
     */
    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalidate();
                        }
                    });
        } else {
            invalidate();
        }
    }

    // ============================================================
    // 加载
    // ============================================================

    /**
     * 懒加载 + 双检锁。
     *
     * <p>不放在 {@code @PostConstruct} 里：启动时知识库通常是空的，
     * 白查一次；而且启动阶段数据库刚连上，多一点查询就多一分启动失败的风险。
     * 第一次检索时加载，代价也就那一次。
     */
    private List<IndexedChunk> index() {
        List<IndexedChunk> local = index;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (index != null) {
                return index;
            }
            index = load();
            return index;
        }
    }

    private List<IndexedChunk> load() {
        List<KnowledgeChunk> chunks = chunkRepository.findAllForSearch();
        List<IndexedChunk> loaded = new ArrayList<>(chunks.size());
        for (KnowledgeChunk chunk : chunks) {
            float[] vector = EmbeddingCodec.decode(chunk.getEmbedding());
            if (vector.length == 0) {
                // 空向量的块没法参与比对。跳过而不是让它参与 ——
                // 参与的话余弦恒为 -1，会稳定排在最后，看着像"不相关"，
                // 掩盖了"这条数据本身是坏的"
                log.warn("文本块 {} 的向量是空的，已跳过", chunk.getId());
                continue;
            }
            loaded.add(new IndexedChunk(
                    chunk.getId(),
                    chunk.getKnowledgeId(),
                    chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                    chunk.getContent(),
                    vector,
                    chunk.getEmbedModel() == null ? "" : chunk.getEmbedModel(),
                    chunk.getDimension() == null ? vector.length : chunk.getDimension()));
        }
        log.info("知识库向量索引已加载：{} 块", loaded.size());
        return List.copyOf(loaded);
    }

    // ============================================================
    // 内部类型
    // ============================================================

    private record IndexedChunk(Long chunkId, Long knowledgeId, int chunkIndex,
                               String content, float[] vector, String model, int dimension) {
    }

    /** 一条命中的块 */
    public record ChunkMatch(Long chunkId, Long knowledgeId, int chunkIndex,
                             String content, double score) {
    }

    /**
     * 检索结果。
     *
     * <p>把"跳过了多少块"一起带出来，是为了能告诉用户真相：
     * 库里明明有 500 块却一块都没命中，如果不说，看起来就是"没搜到"；
     * 说清楚"其中 480 块是用另一个嵌入模型生成的，需要重新入库"，
     * 用户才知道该做什么。
     *
     * <p><b>{@code bestScore} 是"最像的那一条得了几分"，不管有没有过门槛。</b>
     * 这一个数就决定了"没有命中"是可调的还是真的没有：
     * 门槛 0.35 而最像的一条只有 0.28，那是门槛该往下调；
     * 最像的只有 0.05，那是库里确实没有相关内容。
     * 没有它的话，用户只能对着"什么也没搜到"干瞪眼 ——
     * 而门槛调高了是个静态配置错误，不会以任何形式报出来。
     */
    public record SearchOutcome(List<ChunkMatch> matches, int skippedMismatch,
                                int totalIndexed, double bestScore) {
    }
}
