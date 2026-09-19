package com.yan.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 故障分析的结构化结果。
 *
 * <p>对应发给 Ollama 的 JSON Schema。因为请求里用 format 传了 schema，
 * 模型输出的 JSON 结构是受约束的，不会出现字段缺失或类型不对的情况；
 * 但**字段内容的质量取决于模型本身** —— 3B 小模型的判断可能不准，
 * 所以界面上要明确标注这是"AI 建议"，不能当成维修结论。
 */
public record AiFaultAnalysisResult(
        /** 严重程度：高 / 中 / 低 */
        String severity,
        /** 可能原因，每条一句话 */
        List<String> possibleCauses,
        /** 建议维修步骤，按顺序排列 */
        List<String> suggestedSteps,
        /** 预计工时（小时） */
        BigDecimal estimatedHours
) {
}
