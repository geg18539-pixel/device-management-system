package com.yan.backend.service.impl;

import com.yan.backend.config.DiagnosisProperties;
import com.yan.backend.config.KnowledgeProperties;
import com.yan.backend.diagnosis.SimilarCaseFinder;
import com.yan.backend.dto.DiagnosisContextVO;
import com.yan.backend.dto.DiagnosisRequest;
import com.yan.backend.dto.KnowledgeSearchHitVO;
import com.yan.backend.dto.SimilarCaseVO;
import com.yan.backend.dto.SuggestedPartVO;
import com.yan.backend.entity.Device;
import com.yan.backend.entity.DeviceKnowledge;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;
import com.yan.backend.ai.provider.AiProviderRegistry;
import com.yan.backend.ai.provider.ChatMessage;
import com.yan.backend.ai.provider.ChatRequest;
import com.yan.backend.ai.provider.EmbeddingProvider;
import com.yan.backend.ai.provider.LlmProvider;
import com.yan.backend.knowledge.KnowledgeVectorStore;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.repository.KnowledgeRepository;
import com.yan.backend.repository.SparePartRecordRepository;
import com.yan.backend.repository.SparePartRepository;
import com.yan.backend.service.AiSettingsService;
import com.yan.backend.service.DiagnosisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障诊断。
 *
 * <p><b>⚠️ 没有类级 {@code @Transactional}</b>：检索要做一次嵌入（HTTP 调用），
 * 生成是几十秒的流式调用。包在事务里会一直占着数据库连接
 * （Hikari 默认池 10 条）。这个坑项目里已经踩过两次。
 *
 * <p><b>提示词的核心是"只依据资料，不要编造"</b>。
 * 一个 3B 小模型面对"没有资料"时的默认行为是**顺着问题的语气编一个像样的答案**，
 * 那比说"我不知道"危害大得多 —— 运维照着编的步骤去拆设备是要出事的。
 * 所以提示词里明确要求：资料里没有的就直说没有。
 * 这一点光靠模型自觉不保险，所以界面上**必须把检索到的原文一并展示**，
 * 让人能自己核对（这也是 retrieve 单独开一个接口的原因）。
 */
@Service
public class DiagnosisServiceImpl implements DiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosisServiceImpl.class);

    /** 相似工单最多取几条去做备件聚合。取太多的话"在 20 条工单里用过"就没信息量了 */
    private static final int PART_LOOKUP_CASE_LIMIT = 10;

    private final DeviceRepository deviceRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final SparePartRepository sparePartRepository;
    private final SparePartRecordRepository sparePartRecordRepository;
    private final SimilarCaseFinder similarCaseFinder;
    private final KnowledgeVectorStore vectorStore;
    /**
     * 提供方**不固定**在字段上：管理员可能在界面上换提供方或模型，
     * 每次调用现从注册表取。固定住的话换完不生效，而且不报错。
     */
    private final AiProviderRegistry providerRegistry;
    private final DiagnosisProperties properties;
    private final KnowledgeProperties knowledgeProperties;

    private final AiSettingsService settings;

    public DiagnosisServiceImpl(DeviceRepository deviceRepository,
                                KnowledgeRepository knowledgeRepository,
                                SparePartRepository sparePartRepository,
                                SparePartRecordRepository sparePartRecordRepository,
                                SimilarCaseFinder similarCaseFinder,
                                KnowledgeVectorStore vectorStore,
                                AiProviderRegistry providerRegistry,
                                DiagnosisProperties properties,
                                KnowledgeProperties knowledgeProperties,
                                AiSettingsService settings) {
        this.deviceRepository = deviceRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.sparePartRepository = sparePartRepository;
        this.sparePartRecordRepository = sparePartRecordRepository;
        this.similarCaseFinder = similarCaseFinder;
        this.vectorStore = vectorStore;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.knowledgeProperties = knowledgeProperties;
        this.settings = settings;
    }

    // ============================================================
    // 检索
    // ============================================================

    @Override
    public DiagnosisContextVO retrieve(String faultDesc, String faultType, Long deviceId) {
        DiagnosisContextVO vo = new DiagnosisContextVO();
        vo.setFaultDesc(faultDesc);
        vo.setDeviceId(deviceId);

        if (deviceId != null) {
            deviceRepository.findById(deviceId).ifPresent(device -> {
                vo.setDeviceName(device.getDeviceName());
                vo.setDeviceModel(buildDeviceSummary(device));
            });
        }

        List<KnowledgeSearchHitVO> hits = searchKnowledge(faultDesc);
        vo.setKnowledgeHits(hits);

        List<SimilarCaseVO> cases = similarCaseFinder.find(faultDesc, faultType, deviceId);
        vo.setSimilarCases(cases);

        vo.setSuggestedParts(suggestParts(cases));
        vo.setNotice(buildNotice(hits.size(), cases.size()));
        return vo;
    }

    /**
     * 查知识库。**检索失败不影响整个诊断** —— 知识库为空、嵌入模型没拉下来，
     * 这些情况下相似历史工单仍然是有用的。所以这里吞掉异常只记日志，
     * 让诊断退化成"只基于历史工单"。
     */
    private List<KnowledgeSearchHitVO> searchKnowledge(String faultDesc) {
        if (!StringUtils.hasText(faultDesc)) {
            return List.of();
        }
        try {
            EmbeddingProvider embedding = providerRegistry.currentEmbedding();
            float[] queryVector = embedding.embedOne(faultDesc);
            KnowledgeVectorStore.SearchOutcome outcome = vectorStore.search(
                    queryVector, embedding.modelName(), queryVector.length,
                    properties.getKnowledgeTopK(), knowledgeProperties.getMinScore());

            List<KnowledgeSearchHitVO> hits = new ArrayList<>();
            for (KnowledgeVectorStore.ChunkMatch match : outcome.matches()) {
                KnowledgeSearchHitVO hit = new KnowledgeSearchHitVO();
                hit.setChunkId(match.chunkId());
                hit.setKnowledgeId(match.knowledgeId());
                hit.setChunkIndex(match.chunkIndex());
                hit.setContent(match.content());
                hit.setScore(match.score());
                hits.add(hit);
            }
            // 文档标题在这里补齐（向量存储只管块，不关心标题）
            fillDocTitles(hits);
            return hits;
        } catch (Exception ex) {
            log.warn("诊断时查询知识库失败，本次只用历史工单：{}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * 补上文档标题。
     *
     * <p>向量存储只管块，不关心它属于哪份文档 —— 让存储层去 join 标题
     * 会把"检索"和"展示"两件事混在一起。这里单独查一次：
     * 知识库文档本来就不多（几十份），全表取无所谓。
     */
    private void fillDocTitles(List<KnowledgeSearchHitVO> hits) {
        if (hits.isEmpty()) {
            return;
        }
        Map<Long, String> titles = new HashMap<>();
        for (DeviceKnowledge doc : knowledgeRepository.findAll()) {
            titles.put(doc.getId(), doc.getTitle());
        }
        for (KnowledgeSearchHitVO hit : hits) {
            hit.setDocTitle(titles.getOrDefault(hit.getKnowledgeId(), "（文档已删除）"));
        }
    }

    // ============================================================
    // 建议备件
    // ============================================================

    /**
     * 从相似工单**实际领用过**的配件里聚合出建议。
     *
     * <p>不是让模型凭空说该换什么：模型不知道你库里有什么件、哪个还有库存。
     * 这里给的是真实发生过的领用记录，还带上当前库存 ——
     * 建议你换一个库存为 0 的件是没用的。
     */
    private List<SuggestedPartVO> suggestParts(List<SimilarCaseVO> cases) {
        List<Long> repairIds = cases.stream()
                .map(SimilarCaseVO::getRepairId)
                .limit(PART_LOOKUP_CASE_LIMIT)
                .toList();
        if (repairIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> usageByPart = new HashMap<>();
        for (SparePartRecord record : sparePartRecordRepository
                .findByRelatedRepairIdInOrderByRecordTimeDesc(repairIds)) {
            // 只数出库：入库是补货，和"这次故障用掉了什么"无关
            if (record.getPartId() != null && SparePartRecord.TYPE_OUT.equals(record.getRecordType())) {
                usageByPart.merge(record.getPartId(), 1, Integer::sum);
            }
        }
        if (usageByPart.isEmpty()) {
            return List.of();
        }

        Map<Long, SparePart> parts = new HashMap<>();
        for (SparePart part : sparePartRepository.findAllById(usageByPart.keySet())) {
            parts.put(part.getId(), part);
        }

        List<SuggestedPartVO> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : usageByPart.entrySet()) {
            SparePart part = parts.get(entry.getKey());
            if (part == null) {
                // 配件被删了（理论上不会 —— 有流水的配件不允许删除），跳过
                continue;
            }
            SuggestedPartVO vo = new SuggestedPartVO();
            vo.setPartId(part.getId());
            vo.setPartCode(part.getPartCode());
            vo.setPartName(part.getPartName());
            vo.setUnit(part.getUnit());
            vo.setStockQuantity(part.getStockQuantity());
            vo.setUsageCount(entry.getValue());
            result.add(vo);
        }
        result.sort(Comparator.comparingInt(SuggestedPartVO::getUsageCount).reversed());
        return result;
    }

    // ============================================================
    // 提示词
    // ============================================================

    private String buildDeviceSummary(Device device) {
        StringBuilder sb = new StringBuilder();
        append(sb, device.getDeviceName());
        append(sb, device.getModel());
        append(sb, device.getManufacturer());
        append(sb, device.getLocation());
        append(sb, device.getLifecycleStatus());
        return sb.isEmpty() ? null : sb.toString();
    }

    private void append(StringBuilder sb, String value) {
        if (StringUtils.hasText(value)) {
            if (!sb.isEmpty()) {
                sb.append(" / ");
            }
            sb.append(value);
        }
    }

    /**
     * 把检索结果拼成提示词。
     *
     * <p>结构上刻意把"资料"和"问题"分开、并明确编号 —— 小模型对
     * "下面这些是参考资料"这种分节比长段落敏感得多。
     */
    String buildPrompt(DiagnosisContextVO context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是设备维修专家。运维人员报告了一条故障，请给出诊断建议。\n\n");

        sb.append("【故障描述】\n").append(context.getFaultDesc()).append("\n\n");

        if (context.getDeviceName() != null) {
            sb.append("【设备信息】\n").append(context.getDeviceName());
            if (context.getDeviceModel() != null) {
                sb.append("（").append(context.getDeviceModel()).append("）");
            }
            sb.append("\n\n");
        } else {
            sb.append("【设备信息】\n未指定具体设备\n\n");
        }

        if (context.getKnowledgeHits().isEmpty()) {
            sb.append("【参考资料：设备手册片段】\n（知识库里没有检索到相关内容）\n\n");
        } else {
            sb.append("【参考资料：设备手册片段】\n");
            int i = 1;
            for (KnowledgeSearchHitVO hit : context.getKnowledgeHits()) {
                sb.append("[").append(i++).append("] 来自《").append(hit.getDocTitle())
                        .append("》第 ").append(hit.getChunkIndex() + 1).append(" 段：\n")
                        .append(hit.getContent()).append("\n\n");
            }
        }

        if (context.getSimilarCases().isEmpty()) {
            sb.append("【参考资料：相似历史工单】\n（没有找到相似的历史工单）\n\n");
        } else {
            sb.append("【参考资料：相似历史工单】\n");
            int i = 1;
            for (SimilarCaseVO c : context.getSimilarCases()) {
                sb.append("[").append(i++).append("] 设备：").append(c.getDeviceName());
                if (c.getMatchReasons() != null && !c.getMatchReasons().isEmpty()) {
                    sb.append("（").append(String.join("、", c.getMatchReasons())).append("）");
                }
                sb.append("\n    现象：").append(c.getFaultDesc())
                        .append("\n    处理结果：")
                        .append(StringUtils.hasText(c.getRepairResult()) ? c.getRepairResult() : "（未填写）")
                        .append("\n\n");
            }
        }

        if (!context.getSuggestedParts().isEmpty()) {
            sb.append("【相似工单实际领用过的备件】\n");
            for (SuggestedPartVO part : context.getSuggestedParts()) {
                sb.append("- ").append(part.getPartName());
                if (StringUtils.hasText(part.getPartCode())) {
                    sb.append("（").append(part.getPartCode()).append("）");
                }
                sb.append("　当前库存 ").append(part.getStockQuantity())
                        .append("，在 ").append(part.getUsageCount()).append(" 条相似工单里用过\n");
            }
            sb.append('\n');
        }

        sb.append("""
                【要求】
                1. 只依据上面提供的资料回答。资料里没有的信息，明确说「资料里没有」，不要编造。
                2. 按「可能原因」「排查步骤」「建议更换备件」「安全提示」四部分组织，简洁中文分点写。
                3. 排查步骤要具体可操作，按执行顺序排列。
                4. 如果资料不足以判断，直接说明还需要补充哪些信息，不要硬给结论。
                """);
        return sb.toString();
    }

    private String buildNotice(int knowledgeHits, int similarCases) {
        if (knowledgeHits == 0 && similarCases == 0) {
            return "没有检索到任何参考资料 —— 知识库可能是空的，历史工单里也没有相似案例。"
                    + "这种情况下模型只能泛泛而谈，建议先去「设备知识库」上传相关手册";
        }
        if (knowledgeHits == 0) {
            return "知识库里没有检索到相关内容（可能还没上传对应手册），下面的建议主要依据历史工单";
        }
        if (similarCases == 0) {
            return "没有找到相似的历史工单，下面的建议主要依据设备手册";
        }
        return null;
    }

    // ============================================================
    // 流式生成
    // ============================================================

    /**
     * 流式生成诊断建议。
     *
     * <p><b>这个流里只有模型生成的文本，没有检索摘要。</b>
     * 检索到的资料由 {@code /retrieve} 单独返回，前端用**结构化的面板**展示
     * （相似工单能点进去、备件带当前库存）—— 比在这里拼一段纯文本摘要有用得多。
     * 两处都输出的话，同一份信息会在页面上显示两遍。
     *
     * <p>代价是一次诊断会走两次检索（前端先调 /retrieve 再调 /stream）。
     * 检索本身是"一次短文本嵌入 + 一次有上限的查库"，秒级以内，可以接受；
     * 换来的是"模型依据什么"这件事能被结构化地呈现出来。
     *
     * <p>走的是 {@link LlmProvider#streamRound}，所以**换提供方（本机 Ollama /
     * 云端 OpenAI 兼容服务）这个方法一行都不用改** —— 流式格式的差异
     * （NDJSON 还是 SSE）由提供方自己消化。
     */
    @Override
    public void streamDiagnosis(DiagnosisRequest request, OutputStream outputStream)
            throws IOException {

        DiagnosisContextVO context = retrieve(
                request.getFaultDesc(), request.getFaultType(), request.getDeviceId());

        LlmProvider provider = providerRegistry.currentLlm();
        ChatRequest chatRequest = ChatRequest.plain(
                request.getModel(),
                List.of(
                        ChatMessage.system("你是设备维修专家，回答简洁、分点、用中文。"),
                        ChatMessage.user(buildPrompt(context))),
                // 诊断要的是稳定结论，不是创意
                settings.diagnosisTemperature(),
                settings.diagnosisMaxTokens(),
                settings.chatThinking());

        long start = System.currentTimeMillis();
        log.info("故障诊断开始：提供方={}, 设备={}, 知识库命中={}, 相似工单={}, 建议备件={}",
                provider.id(), context.getDeviceId(), context.getKnowledgeHits().size(),
                context.getSimilarCases().size(), context.getSuggestedParts().size());

        try {
            // 文本分片边收边往响应里写。**不能攒** ——
            // 攒起来就退化成"等几十秒然后一次性出现"了
            provider.streamRound(chatRequest, chunk -> {
                try {
                    outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                    // flush 不能省：不 flush 内容会留在缓冲区里，
                    // 直到整个响应结束才发出去
                    outputStream.flush();
                } catch (IOException ex) {
                    // Consumer 不能抛检查异常，包一层出去；外面统一转成"诊断失败"
                    throw new UncheckedIOException(ex);
                }
            });
            log.info("故障诊断结束：耗时={}ms", System.currentTimeMillis() - start);
        } catch (Exception ex) {
            log.warn("故障诊断生成失败：{}", ex.getMessage());
            writeError(outputStream, ex);
        }
    }

    /**
     * 生成失败时往流里写一段可读的说明。
     *
     * <p><b>不抛异常</b>：响应头早就发出去了，状态码改不了，
     * 只能把失败原因当正文写出去，前端至少能显示给用户看。
     * 具体是"哪一家、哪个地址"由提供方在上抛的异常里说清楚。
     */
    private void writeError(OutputStream outputStream, Exception ex) {
        String text = "\n\n[诊断失败]" + ex.getMessage()
                + "。请去「系统设置 → AI 模型」检查当前提供方和地址，"
                + "本机 Ollama 的话确认它已启动（命令 ollama list 能列出模型）。";
        try {
            outputStream.write(text.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException ignored) {
            // 浏览器那边已经断了，没什么可做的
        }
    }
}
