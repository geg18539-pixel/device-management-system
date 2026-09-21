package com.yan.backend.repository;

import com.yan.backend.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    /** 某个文档的全部块，按序号排。删文档时级联清理、查看块内容都用它 */
    List<KnowledgeChunk> findByKnowledgeIdOrderByChunkIndexAsc(Long knowledgeId);

    /** 某文档的块数。删文档前确认、页面展示都用 */
    long countByKnowledgeId(Long knowledgeId);

    /**
     * 删掉某个文档的全部块。
     *
     * <p>用批量 DELETE 而不是先查出来再逐条删：一个几百页的 PDF 能切出上千块，
     * 逐条删会发上千条 DELETE。
     *
     * <p>{@code @Modifying} 必须自己带 {@code @Transactional}（Spring Data 的
     * {@code delete*} 派生方法自带事务，但这个自定义 @Modifying 没有）。
     */
    @Modifying
    @Query("delete from KnowledgeChunk c where c.knowledgeId = :knowledgeId")
    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 全部块的向量。
     *
     * <p>检索要在内存里逐条算余弦，所以一次全取。
     * 演示规模（几千块）下这是几 MB 的读取，可以接受；
     * 真上量的话这里要换成本地向量索引或向量数据库。
     */
    @Query("select c from KnowledgeChunk c order by c.knowledgeId, c.chunkIndex")
    List<KnowledgeChunk> findAllForSearch();
}
