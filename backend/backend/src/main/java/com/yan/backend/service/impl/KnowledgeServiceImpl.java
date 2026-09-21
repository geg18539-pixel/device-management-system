package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.config.KnowledgeProperties;
import com.yan.backend.dto.KnowledgeSearchHitVO;
import com.yan.backend.dto.KnowledgeSearchResultVO;
import com.yan.backend.dto.KnowledgeVO;
import com.yan.backend.dto.StoredFileVO;
import com.yan.backend.entity.DeviceKnowledge;
import com.yan.backend.entity.KnowledgeChunk;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.EmbeddingProvider;
import com.yan.backend.knowledge.DocumentTextExtractor;
import com.yan.backend.knowledge.EmbeddingCodec;
import com.yan.backend.knowledge.KnowledgeVectorStore;
import com.yan.backend.knowledge.TextChunker;
import com.yan.backend.repository.KnowledgeChunkRepository;
import com.yan.backend.repository.KnowledgeRepository;
import com.yan.backend.service.FileStorageService;
import com.yan.backend.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备知识库。
 *
 * <p><b>⚠️ 这个类刻意没有类级 {@code @Transactional}。</b>
 *
 * <p>上传要经过「解析 → 切分 → 逐批调 Ollama 嵌入」三步，最后一步是
 * 几秒到几十秒的**外部 HTTP 调用**。整个流程包在一个事务里的话，
 * 这段时间会一直占着一条数据库连接 —— Hikari 默认池只有 10 条，
 * 几个人同时传文档就能把池占满，整个系统跟着排队。
 * 这个坑在 AI 故障分析那边已经踩过一次（见 AiFaultAnalysisServiceImpl 的说明）。
 *
 * <p>所以：网络调用、文件 IO 一律在事务之外；每次 {@code repository.save}
 * 各自是一个小事务。代价是"建文档行 → 写块 → 更新状态"不是原子的，
 * 但这反而是想要的 —— 中途挂掉时文档会停在「处理中」，
 * 是个能看出问题、也能重新处理的状态。
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    /** 一次往库里写多少个块。整批 saveAll 会把这些实体全压在持久化上下文里 */
    private static final int SAVE_BATCH = 200;

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final FileStorageService fileStorageService;
    private final KnowledgeProperties properties;
    private final DocumentTextExtractor extractor;
    private final TextChunker chunker;
    /**
     * 嵌入提供方**不在这里固定** —— 管理员可能在界面上换提供方或模型，
     * 每次用的时候现从注册表取。缓存住的话换完不生效，而且不报错。
     */
    private final AiProviderRegistry providerRegistry;
    private final KnowledgeVectorStore vectorStore;

    /**
     * 用来**显式圈出事务边界**。
     *
     * <p>为什么不用 {@code @Transactional} 注解：上传流程要调 Ollama（几十秒），
     * 不能整个包在事务里；但其中"写块 + 改状态"这一段又必须是原子的
     * （要么全写进去，要么一块都没有，不能留半份）。
     *
     * <p>为什么不把那段抽到另一个 bean 上用注解解决：{@code @Transactional}
     * 基于代理，**同一个类内部的自调用不经过代理**，注解会静默失效 ——
     * 这个项目已经为此踩过三次（OperLogRecorder / AI 工具执行 / 数据种子）。
     * 与其再拆一个只有两个方法的类，不如就地用 TransactionTemplate，
     * 事务边界一眼看得见，也不会因为"以后有人把方法挪了个位置"而失效。
     */
    private final TransactionTemplate transactionTemplate;

    public KnowledgeServiceImpl(KnowledgeRepository knowledgeRepository,
                                KnowledgeChunkRepository chunkRepository,
                                FileStorageService fileStorageService,
                                KnowledgeProperties properties,
                                DocumentTextExtractor extractor,
                                TextChunker chunker,
                                AiProviderRegistry providerRegistry,
                                KnowledgeVectorStore vectorStore,
                                PlatformTransactionManager transactionManager) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.fileStorageService = fileStorageService;
        this.properties = properties;
        this.extractor = extractor;
        this.chunker = chunker;
        this.providerRegistry = providerRegistry;
        this.vectorStore = vectorStore;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ============================================================
    // 查询
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeVO> list(String keyword) {
        List<DeviceKnowledge> all = knowledgeRepository.findAllByOrderByUploadTimeDesc();
        if (!StringUtils.hasText(keyword)) {
            return all.stream().map(this::toVO).toList();
        }
        String like = keyword.trim().toLowerCase();
        return all.stream()
                .filter(d -> contains(d.getTitle(), like) || contains(d.getFileName(), like))
                .map(this::toVO)
                .toList();
    }

    private boolean contains(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> chunksOf(Long id) {
        requireDoc(id);
        return chunkRepository.findByKnowledgeIdOrderByChunkIndexAsc(id)
                .stream().map(KnowledgeChunk::getContent).toList();
    }

    // ============================================================
    // 上传 / 重新处理
    // ============================================================

    @Override
    public KnowledgeVO upload(MultipartFile file, String title) {
        validate(file);

        // 先落盘再建记录：反过来（先建记录再落盘）万一落盘失败，
        // 库里会留一条指向不存在文件的记录
        StoredFileVO stored = fileStorageService.store(file);

        DeviceKnowledge doc = new DeviceKnowledge();
        doc.setTitle(resolveTitle(title, stored.getOriginalName()));
        doc.setFileName(stored.getOriginalName());
        doc.setStoredName(stored.getStoredName());
        doc.setFileType(extensionOf(stored.getOriginalName()));
        doc.setFileSize(stored.getSize());
        doc.setStatus(DeviceKnowledge.STATUS_PROCESSING);
        doc.setChunkCount(0);
        String operator = UserContext.getUsername();
        doc.setUploader(StringUtils.hasText(operator) ? operator : "未知用户");
        doc.setUploadTime(LocalDateTime.now());
        doc.setCreateTime(LocalDateTime.now());
        knowledgeRepository.save(doc);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            return failAndRethrow(doc, "读取上传文件失败：" + ex.getMessage());
        }

        try {
            process(doc, content);
        } catch (RuntimeException ex) {
            // 标记失败后**照样抛出去**：上传接口不能返回 200，
            // 否则前端会提示"上传成功"，而实际上一条块都没入库。
            // 失败信息同时留在库里（列表里能看到原因）和错误响应里
            return failAndRethrow(doc, ex.getMessage());
        }
        return toVO(doc);
    }

    @Override
    public KnowledgeVO reprocess(Long id) {
        DeviceKnowledge doc = requireDoc(id);

        // 清掉旧的块 + 状态改回「处理中」，两件事一起提交。
        // ⚠️ 这个 delete 是 @Modifying 查询，**必须在事务里跑** ——
        // 而 reprocess 本身不能加 @Transactional（后面要调 Ollama），
        // 所以用 TransactionTemplate 把这一小段圈起来
        transactionTemplate.executeWithoutResult(status -> {
            // 旧块可能是上次嵌到一半失败留下的，也可能是换了嵌入模型要重来
            chunkRepository.deleteByKnowledgeId(id);
            doc.setStatus(DeviceKnowledge.STATUS_PROCESSING);
            doc.setErrorMsg(null);
            knowledgeRepository.save(doc);
        });

        byte[] content;
        try {
            // 从磁盘读回原文件。失败时文件是**特意保留**的 ——
            // 用户多半是因为"嵌入模型没拉下来"才失败，拉完之后回来点一下
            // 「重新处理」就行，不用重新上传
            content = fileStorageService.load(doc.getStoredName()).getInputStream().readAllBytes();
        } catch (Exception ex) {
            return failAndRethrow(doc, "读回原文件失败：" + ex.getMessage());
        }

        try {
            process(doc, content);
        } catch (RuntimeException ex) {
            return failAndRethrow(doc, ex.getMessage());
        }
        return toVO(doc);
    }

    /**
     * 解析 → 切分 → 嵌入 → 落块。
     *
     * <p>**不在事务里**（见类注释）。中间那步调 Ollama 可能要几十秒。
     */
    private void process(DeviceKnowledge doc, byte[] content) {
        String text = extractor.extract(content, doc.getFileType());

        List<String> chunks = chunker.chunk(text,
                properties.getChunkSize(), properties.getChunkOverlap(),
                properties.getMaxChunksPerDoc());
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文件里没有可以切分的正文内容");
        }
        if (chunks.size() >= properties.getMaxChunksPerDoc()) {
            // 说清楚是被截断了，而不是让用户以为全文都进去了
            log.warn("文档 {} 的文本块达到上限 {}，超出的部分已丢弃",
                    doc.getId(), properties.getMaxChunksPerDoc());
        }

        List<float[]> vectors = embedding().embed(chunks);
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("嵌入结果条数和文本块对不上");
        }
        int dimension = vectors.get(0).length;
        for (float[] v : vectors) {
            if (v.length != dimension) {
                throw new IllegalStateException("同一个文档里出现了不同维度的向量");
            }
        }

        List<KnowledgeChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setKnowledgeId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(EmbeddingCodec.encode(vectors.get(i)));
            chunk.setDimension(dimension);
            chunk.setEmbedModel(embedding().modelName());
            chunk.setCreateTime(LocalDateTime.now());
            entities.add(chunk);
        }

        // ★ 写块 + 改状态必须在**同一个事务**里。
        // 分开提交的话，如果写到一半失败，库里会留下半份块，
        // 而文档状态还是「处理中」—— 检索能搜到这半份内容，
        // 用户却以为这份文档没入库
        transactionTemplate.executeWithoutResult(status -> {
            for (int start = 0; start < entities.size(); start += SAVE_BATCH) {
                chunkRepository.saveAll(entities.subList(start,
                        Math.min(entities.size(), start + SAVE_BATCH)));
            }
            doc.setChunkCount(entities.size());
            doc.setEmbedModel(embedding().modelName());
            doc.setDimension(dimension);
            doc.setStatus(DeviceKnowledge.STATUS_DONE);
            doc.setErrorMsg(null);
            knowledgeRepository.save(doc);
        });

        // 让检索缓存下次重新加载。走到这里事务已经提交了
        vectorStore.invalidate();
        log.info("知识库文档「{}」入库完成：{} 块，{} 维，模型 {}",
                doc.getTitle(), entities.size(), dimension, embedding().modelName());
    }

    /**
     * 标记失败并把异常继续抛出去。
     *
     * <p>异常消息可能很长（比如 HTTP 客户端把整个响应打出来），截断到列的宽度 ——
     * 超长的话写入会直接报错，把"处理失败"变成"连失败原因都存不下"。
     */
    private KnowledgeVO failAndRethrow(DeviceKnowledge doc, String message) {
        String reason = message == null ? "未知错误" : message;
        if (reason.length() > 480) {
            reason = reason.substring(0, 480) + "…";
        }
        doc.setStatus(DeviceKnowledge.STATUS_FAILED);
        doc.setErrorMsg(reason);
        knowledgeRepository.save(doc);
        throw new IllegalStateException(reason);
    }

    // ============================================================
    // 删除
    // ============================================================

    @Override
    @Transactional
    public void delete(Long id) {
        DeviceKnowledge doc = requireDoc(id);

        chunkRepository.deleteByKnowledgeId(id);
        knowledgeRepository.delete(doc);
        // 磁盘文件一并删掉。不删的话，反复上传删除会在 uploads 目录里
        // 积一堆没人引用的孤儿文件，而且不会有任何地方报出来
        fileStorageService.delete(doc.getStoredName());

        vectorStore.invalidateAfterCommit();
    }

    // ============================================================
    // 检索
    // ============================================================

    /**
     * 语义检索。
     *
     * <p>**同样不加 @Transactional**：把问题转成向量要调 Ollama，
     * 在事务里做会一直占着数据库连接（理由同类注释）。
     * 文档标题的映射单独查一次，那次查询自己是一个事务。
     */
    @Override
    public KnowledgeSearchResultVO search(String query, Integer topK) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("查询内容不能为空");
        }
        String q = query.trim();
        int k = (topK == null || topK <= 0) ? properties.getTopK() : Math.min(topK, 50);

        float[] queryVector = embedding().embedOne(q);
        String model = embedding().modelName();

        KnowledgeVectorStore.SearchOutcome outcome = vectorStore.search(
                queryVector, model, queryVector.length, k, properties.getMinScore());

        Map<Long, String> titles = loadTitleMap();

        List<KnowledgeSearchHitVO> hits = new ArrayList<>(outcome.matches().size());
        for (KnowledgeVectorStore.ChunkMatch match : outcome.matches()) {
            KnowledgeSearchHitVO hit = new KnowledgeSearchHitVO();
            hit.setChunkId(match.chunkId());
            hit.setKnowledgeId(match.knowledgeId());
            hit.setDocTitle(titles.getOrDefault(match.knowledgeId(), "（文档已删除）"));
            hit.setChunkIndex(match.chunkIndex());
            hit.setContent(match.content());
            hit.setScore(match.score());
            hits.add(hit);
        }

        KnowledgeSearchResultVO result = new KnowledgeSearchResultVO();
        result.setQuery(q);
        result.setEmbedModel(model);
        result.setDimension(queryVector.length);
        result.setHits(hits);
        result.setSkippedMismatch(outcome.skippedMismatch());
        result.setTotalIndexed(outcome.totalIndexed());
        result.setBestScore(outcome.bestScore());
        result.setNotice(buildNotice(outcome, hits.size()));
        return result;
    }

    /**
     * 给前端一句能照着做的提示。
     *
     * <p>三种"没结果"的原因完全不同，混成一句"没有找到相关内容"等于什么都没说：
     * 库是空的 → 先去传文档；向量被跳过了 → 去重新入库；
     * 门槛调高了 → 去调门槛；确实没有 → 换个问法。
     *
     * <p>**"门槛调高了"这一句是刻意加的**：minimum score 是个静态配置，
     * 设错了不会以任何形式报出来，只会让检索静默地什么都返回不了 ——
     * 表现和"知识库是空的"一模一样。把"最相近的一段是多少分"说出来，
     * 用户一眼就知道该调门槛还是该补资料。
     */
    private String buildNotice(KnowledgeVectorStore.SearchOutcome outcome, int hitCount) {
        if (outcome.totalIndexed() == 0) {
            return "知识库里还没有任何内容，先在「文档管理」里上传设备手册或维修资料";
        }
        if (outcome.skippedMismatch() > 0 && outcome.skippedMismatch() == outcome.totalIndexed()) {
            return "所有 " + outcome.totalIndexed() + " 个文本块的向量都是用别的嵌入模型生成的，"
                    + "和当前配置（" + embedding().modelName() + "）对不上。"
                    + "请对这些文档点「重新处理」";
        }
        if (hitCount == 0) {
            return "没有找到相关度达到 " + properties.getMinScore() + " 的内容"
                    + "（最相近的一段是 " + String.format("%.3f", outcome.bestScore()) + "）。"
                    + "差距不大就说明门槛定高了，可以调低 app.knowledge.min-score；"
                    + "差得远就是确实没有相关内容，换个说法试试";
        }
        return null;
    }

    /**
     * 文档 id → 标题。
     *
     * <p>刻意**不加 {@code @Transactional}**：它是被同类内部调用的，
     * 注解根本不会生效（代理被绕过）—— 挂着它只会让人误以为这里有事务边界。
     * 实际上也不需要：{@code findAll()} 是 Spring Data 的仓库方法，
     * 自带只读事务。
     */
    private Map<Long, String> loadTitleMap() {
        Map<Long, String> map = new HashMap<>();
        for (DeviceKnowledge doc : knowledgeRepository.findAll()) {
            map.put(doc.getId(), doc.getTitle());
        }
        return map;
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    /** 当前生效的嵌入提供方 */
    private EmbeddingProvider embedding() {
        return providerRegistry.currentEmbedding();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件为空");
        }
        String extension = extensionOf(file.getOriginalFilename());
        // 这是在附件白名单**之上**再加的一层。附件允许图片和 Office，
        // 但它们读不出文本 —— 收进来只会在解析那一步失败，
        // 不如在这里就说清楚知识库只收什么
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new IllegalArgumentException(
                    "知识库只收 " + String.join(" / ", properties.getAllowedExtensions())
                            + " 格式的文件，收到的是 ." + extension);
        }
    }

    private String resolveTitle(String title, String originalName) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        String name = StringUtils.hasText(originalName) ? originalName : "未命名文档";
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        // 标题列是 200，超长会直接写库失败
        return base.length() > 190 ? base.substring(0, 190) : base;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private DeviceKnowledge requireDoc(Long id) {
        return knowledgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识库文档不存在，id = " + id));
    }

    private KnowledgeVO toVO(DeviceKnowledge doc) {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setStatus(doc.getStatus());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setChunkCount(doc.getChunkCount());
        vo.setEmbedModel(doc.getEmbedModel());
        vo.setDimension(doc.getDimension());
        vo.setUploader(doc.getUploader());
        vo.setUploadTime(doc.getUploadTime());
        return vo;
    }
}
