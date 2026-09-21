package com.yan.backend.diagnosis;

import com.yan.backend.config.DiagnosisProperties;
import com.yan.backend.dto.SimilarCaseVO;
import com.yan.backend.entity.DeviceRepair;
import com.yan.backend.repository.DeviceRepairRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从历史维修工单里找相似案例。
 *
 * <p><b>⚠️ 这里刻意用的是"词面重合 + 元数据加权"，不是语义检索。</b>
 * 界面上也如实写成「匹配度」，不写成「语义相似度」。
 *
 * <p>为什么不做成向量检索（明明知识库那边已经有一套了）：
 * <ul>
 *   <li>工单是**高频写入**的表 —— 每完工一单都要让索引失效，
 *       而每次重建索引都意味着把全表故障描述重新嵌入一遍。
 *       和知识库（低频、几十份文档）完全不是一个量级的成本。</li>
 *   <li>故障描述通常只有一两句话，词面信号本身就比较强
 *       （"主轴异响"和"主轴有异响"重合度很高），语义检索的增量收益有限。</li>
 *   <li>要真做，正确做法是给工单单独建一套向量索引 + 增量更新 + 失效策略，
 *       那是独立的一块工作，不该塞在"把 RAG 接进业务"这一步里顺手做。</li>
 * </ul>
 * 如果以后要做，接口就是 {@link #find} 一个方法，换实现不影响调用方。
 */
@Component
public class SimilarCaseFinder {

    /** 中文按字符二元组切开，英文数字按单词。故障描述短，二元组比单字更有区分度 */
    private static final Pattern ASCII_WORD = Pattern.compile("[a-z0-9]+");

    /** 文本重合度在总分里的权重 */
    private static final double WEIGHT_TEXT = 0.6;
    /** 同一台设备的加成。**单独就能过最低分线**，理由见 DiagnosisProperties */
    private static final double BONUS_SAME_DEVICE = 0.35;
    /** 同类故障的加成 */
    private static final double BONUS_SAME_FAULT_TYPE = 0.15;
    /** 文本重合度到这个值才算"描述高度重合" */
    private static final double HIGH_OVERLAP = 0.5;

    private final DeviceRepairRepository repairRepository;
    private final DiagnosisProperties properties;

    public SimilarCaseFinder(DeviceRepairRepository repairRepository,
                             DiagnosisProperties properties) {
        this.repairRepository = repairRepository;
        this.properties = properties;
    }

    /**
     * 找相似案例。
     *
     * @param faultDesc 本次的故障描述
     * @param faultType 本次的故障类型（字典值，可为 null）
     * @param deviceId  本次涉及的设备（可为 null）
     */
    public List<SimilarCaseVO> find(String faultDesc, String faultType, Long deviceId) {
        Set<String> queryTokens = tokens(faultDesc);
        boolean hasFaultType = faultType != null && !faultType.isBlank();
        if (queryTokens.isEmpty() && deviceId == null && !hasFaultType) {
            // 既没有可比的文字、又不知道是哪台设备、也没有故障类型，
            // 找不出任何有意义的东西。与其返回一堆随机工单，不如老实说没有
            return List.of();
        }

        LocalDateTime since = LocalDateTime.now()
                .minusDays(properties.getSimilarCaseWindowDays());
        PageRequest limit = PageRequest.of(0, properties.getSimilarCaseCandidateLimit());

        // 只拿"走完流程且写了维修结果"的工单。待受理/维修中的还没有结论，
        // 拿它当案例是误导
        List<DeviceRepair> candidates = repairRepository.findCasesSince(
                List.of(DeviceRepair.STATUS_FINISHED, DeviceRepair.STATUS_CLOSED),
                since, limit);

        List<Scored> scored = new ArrayList<>();
        for (DeviceRepair repair : candidates) {
            Scored s = score(repair, queryTokens, faultType, deviceId);
            if (s != null && s.score >= properties.getSimilarCaseMinScore()) {
                scored.add(s);
            }
        }

        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        return scored.stream()
                .limit(Math.max(1, properties.getSimilarCaseTopK()))
                .map(this::toVO)
                .toList();
    }

    // ============================================================
    // 打分
    // ============================================================

    private Scored score(DeviceRepair repair, Set<String> queryTokens,
                         String faultType, Long deviceId) {
        double score = 0;
        List<String> reasons = new ArrayList<>();

        double overlap = overlap(queryTokens, tokens(repair.getFaultDesc()));
        if (overlap > 0) {
            score += overlap * WEIGHT_TEXT;
            // 只把"重合得比较明显"的说出来。重合一点点也列一条原因的话，
            // 每条案例下面都挂着一串理由，反而看不出哪条是真的像
            reasons.add(overlap >= HIGH_OVERLAP ? "描述高度重合" : "描述部分重合");
        }

        if (deviceId != null && deviceId.equals(repair.getDeviceId())) {
            score += BONUS_SAME_DEVICE;
            reasons.add("同一台设备");
        }

        // 故障类型两边都有才比。历史工单里大量是老数据（这一列是后加的、没回填），
        // 那种情况下 repair.getFaultType() 是空的，直接跳过
        if (faultType != null && !faultType.isBlank()
                && faultType.equals(repair.getFaultType())) {
            score += BONUS_SAME_FAULT_TYPE;
            reasons.add("同类故障");
        }

        if (score <= 0) {
            return null;
        }
        return new Scored(repair, Math.min(1.0, score), reasons);
    }

    /**
     * 重合系数：交集大小 / 较小那个集合的大小。
     *
     * <p>用重合系数而不是 Jaccard：故障描述长短差别很大
     * （"异响" vs 一段详细的现象描述），Jaccard 会因为并集太大而把
     * 明显相关的一对算成很低。重合系数看的是"短的那边被覆盖了多少"，
     * 更贴合"这条描述能不能被那条解释"这个问题。
     */
    private double overlap(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = a.size() <= b.size() ? b : a;
        int hit = 0;
        for (String token : smaller) {
            if (larger.contains(token)) {
                hit++;
            }
        }
        return (double) hit / smaller.size();
    }

    /** 切成特征集合：中文二元组 + 英文数字单词 */
    private Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        String lower = text.toLowerCase();

        Matcher matcher = ASCII_WORD.matcher(lower);
        while (matcher.find()) {
            out.add(matcher.group());
        }

        StringBuilder zh = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= '一' && c <= '鿿') {
                zh.append(c);
            }
        }
        for (int i = 0; i + 2 <= zh.length(); i++) {
            out.add(zh.substring(i, i + 2));
        }
        return out;
    }

    private SimilarCaseVO toVO(Scored scored) {
        DeviceRepair repair = scored.repair;
        SimilarCaseVO vo = new SimilarCaseVO();
        vo.setRepairId(repair.getId());
        vo.setDeviceId(repair.getDeviceId());
        // 工单里存的是设备名快照，设备改名后历史仍然反映当时叫法
        vo.setDeviceName(repair.getDeviceName());
        vo.setFaultDesc(repair.getFaultDesc());
        vo.setFaultType(repair.getFaultType());
        vo.setRepairResult(repair.getRepairResult());
        vo.setRepairer(repair.getRepairer());
        vo.setFinishTime(repair.getFinishTime());
        vo.setSimilarity(scored.score);
        vo.setMatchReasons(scored.reasons);
        return vo;
    }

    private record Scored(DeviceRepair repair, double score, List<String> reasons) {
    }
}
