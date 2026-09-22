package com.yan.backend.service;

import com.yan.backend.dto.DiagnosisContextVO;
import com.yan.backend.dto.DiagnosisRequest;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 智能故障诊断：把知识库检索 + 相似历史工单喂给模型，生成维修建议。
 *
 * <p>这是「把 RAG 接进业务」的落点。检索那一步（{@code KnowledgeVectorStore}）
 * 上一批已经做好并单独验证过了，这一步只是把它接到生成上。
 */
public interface DiagnosisService {

    /**
     * 只做检索，不生成。
     *
     * <p>单独暴露出来的意义：运维可以**先看到模型依据的是哪些材料**，
     * 再决定要不要让它写建议。材料本身就不对（比如手册根本没上传）时，
     * 一眼就能看出来，不用对着一段听起来很专业的回答猜它靠不靠谱。
     */
    DiagnosisContextVO retrieve(String faultDesc, String faultType, Long deviceId);

    /**
     * 检索 + 流式生成建议。
     *
     * <p>必须流式：本机小模型在 CPU 上生成一段建议要几十秒，
     * 前端 axios 默认超时 10 秒，同步返回必然失败。
     */
    void streamDiagnosis(DiagnosisRequest request, OutputStream outputStream) throws IOException;
}
