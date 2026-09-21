package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.dto.DiagnosisContextVO;
import com.yan.backend.dto.DiagnosisRequest;
import com.yan.backend.service.DiagnosisService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 智能故障诊断。
 *
 * <p><b>刻意不加 {@code @RequirePerm}，只要求登录</b> ——
 * 和 {@code /api/ai/**} 同一个口径。理由：
 * <ul>
 *   <li>AI 助手从上线的第一天起就是所有登录用户可用的，诊断没有理由突然限管理员；</li>
 *   <li>报修/维修本来就是操作员在做，诊断是给他们用的工具，
 *       限管理员等于把功能做给了不会用它的人；</li>
 *   <li>**更重要的**：这里检索的是知识库，但暴露的只是**命中的片段和文档标题**。
 *       知识库的增删改仍然锁在 {@code sys:knowledge:*} 下面。
 *       这正是当初说好的做法 —— 要给操作员检索能力，就开一个只要求登录的
 *       检索接口，而不是把知识库的 list 权限放开（那会连上传删除一起放开）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/diagnosis")
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    public DiagnosisController(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    /**
     * POST /api/diagnosis/retrieve —— 只检索，不生成。
     *
     * <p>单独开一个接口是刻意的：让运维**先看到模型依据的是哪些材料**。
     * 材料本身就不对（手册没上传、检索没命中）时一眼能看出来，
     * 不用对着一段听起来很专业的回答猜它靠不靠谱。
     *
     * <p>也因为是同步的快接口（只做一次嵌入 + 查库），
     * 前端可以放心地在输入框旁边实时调用。
     */
    @PostMapping("/retrieve")
    public ResponseEntity<Result<DiagnosisContextVO>> retrieve(
            @Valid @RequestBody DiagnosisRequest request) {
        return ResponseEntity.ok(Result.success(diagnosisService.retrieve(
                request.getFaultDesc(), request.getFaultType(), request.getDeviceId())));
    }

    /**
     * POST /api/diagnosis/stream —— 检索 + 流式生成建议。
     *
     * <p>和 {@code /api/ai/chat} 一样，返回 void、直接往 response 的输出流里写。
     * 不能用 {@code StreamingResponseBody} —— 在本项目这套组合上它会静默退化成
     * 一次性返回（实测对比见 AiController 的注释）。
     */
    @PostMapping("/stream")
    public void stream(@Valid @RequestBody DiagnosisRequest request,
                       HttpServletResponse response) throws IOException {

        // 中文必须声明编码，否则浏览器可能按 latin-1 解码，输出变成乱码
        response.setContentType("text/plain;charset=UTF-8");
        // 告诉 nginx 不要缓冲。nginx.conf 里也单独给 /api/diagnosis/ 配了
        // proxy_buffering off —— 两处都做，防止有人改了 nginx 却忘了这里的语义
        response.setHeader("X-Accel-Buffering", "no");

        diagnosisService.streamDiagnosis(request, response.getOutputStream());
    }
}
