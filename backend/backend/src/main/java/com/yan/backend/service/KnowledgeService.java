package com.yan.backend.service;

import com.yan.backend.dto.KnowledgeSearchResultVO;
import com.yan.backend.dto.KnowledgeVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 设备知识库：源文档的管理 + 语义检索。
 *
 * <p>知识库的"知识"全部来自上传的文档，没有一份是编的 ——
 * 检索只返回文档里的原文片段，这一批刻意不接生成。
 */
public interface KnowledgeService {

    /** 全部文档，最新的在前 */
    List<KnowledgeVO> list(String keyword);

    /**
     * 上传并**同步**完成解析、切分、嵌入。
     *
     * <p>同步做而不是异步：知识库文档是低频操作，一份几百页的 PDF
     * 也就几秒到几十秒。异步的话要引入轮询和状态刷新，
     * 对"传一份手册等它解析好"这个场景是过度设计。
     * （对比工单的 AI 分析 —— 那个每次报修都触发、用户在前端等着，
     * 所以必须异步。）
     */
    KnowledgeVO upload(MultipartFile file, String title);

    /**
     * 重新解析并嵌入一个已存在的文档。
     *
     * <p>存在的意义很实际：第一次上传最常见的失败原因是**嵌入模型还没拉下来**，
     * 用户拉完之后不该重新上传一遍。失败时原文件是特意保留的。
     * 换过嵌入模型之后也需要对旧文档跑一遍这个。
     */
    KnowledgeVO reprocess(Long id);

    /** 删除文档：连文本块和磁盘文件一起清掉 */
    void delete(Long id);

    /** 某个文档的全部文本块（纯文本，不含向量）。给"看看切成了什么样"用 */
    List<String> chunksOf(Long id);

    /**
     * 语义检索：把问题转成向量，找出最相似的若干片段。
     *
     * @param topK 返回几条。传 null 用配置里的默认值
     */
    KnowledgeSearchResultVO search(String query, Integer topK);
}
