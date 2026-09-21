package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.KnowledgeSearchResultVO;
import com.yan.backend.dto.KnowledgeVO;
import com.yan.backend.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 设备知识库。
 *
 * <p>这一批（RAG 管道的搭通）**只做到"检索出相关片段"**，不接生成。
 * 原因是检索质量是生成质量的前提：检索出来的片段本身就是错的话，
 * 后面接多好的模型都只是在错误材料上编。把检索单独暴露出来给人看，
 * 才能判断该修检索还是该修提示词。
 *
 * <p>检索接口也挂在 {@code sys:knowledge:list} 下（默认只有管理员）。
 * 以后「智能故障诊断」要让操作员也能检索时，再单独给它开一个
 * 只要求登录的接口 —— 那是个需要有意识做的决定，不该现在顺手放开。
 */
@RequirePerm("sys:knowledge:list")
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** GET /api/knowledge —— 全部文档，最新的在前 */
    @GetMapping
    public ResponseEntity<Result<List<KnowledgeVO>>> list(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(Result.success(knowledgeService.list(keyword)));
    }

    /**
     * POST /api/knowledge —— 上传文档。
     *
     * <p>**同步**完成解析、切分、嵌入，所以接口本身可能要几十秒。
     * 前端的这个请求要把超时单独放大（默认 10 秒必然不够）。
     *
     * <p>处理失败时返回错误（而不是 200 + 状态字段），失败原因同时写进文档记录 ——
     * 返回 200 的话前端会提示"上传成功"，而实际上一条内容都没入库。
     */
    @Log(title = "知识库上传", businessType = "IMPORT")
    @RequirePerm("sys:knowledge:add")
    @PostMapping
    public ResponseEntity<Result<KnowledgeVO>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title) {
        return ResponseEntity.ok(Result.success("文档已入库",
                knowledgeService.upload(file, title)));
    }

    /**
     * POST /api/knowledge/{id}/reprocess —— 重新解析并嵌入。
     *
     * <p>存在的意义很实际：第一次上传最常见的失败原因是**嵌入模型还没拉下来**，
     * 用户拉完之后不该重新上传一遍。失败时原文件是特意保留的，就是给这个用的。
     */
    @Log(title = "知识库重新处理", businessType = "UPDATE")
    @RequirePerm("sys:knowledge:add")
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<Result<KnowledgeVO>> reprocess(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success("已重新处理",
                knowledgeService.reprocess(id)));
    }

    /** GET /api/knowledge/{id}/chunks —— 某个文档切出来的文本块（不含向量） */
    @GetMapping("/{id}/chunks")
    public ResponseEntity<Result<List<String>>> chunks(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(knowledgeService.chunksOf(id)));
    }

    @Log(title = "知识库删除", businessType = "DELETE")
    @RequirePerm("sys:knowledge:remove")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ResponseEntity.ok(Result.success("文档已删除", null));
    }

    /**
     * POST /api/knowledge/search —— 语义检索。
     *
     * <p>用 POST 而不是 GET：查询语句可能很长，塞进 URL 既不好看也有长度限制。
     */
    @PostMapping("/search")
    public ResponseEntity<Result<KnowledgeSearchResultVO>> search(
            @RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? null : String.valueOf(body.get("query"));
        Integer topK = toInt(body.get("topK"));
        return ResponseEntity.ok(Result.success(knowledgeService.search(query, topK)));
    }

    /**
     * 从 JSON 里取数字。
     *
     * <p>⚠️ 不能直接 {@code (Integer) body.get("topK")} —— Jackson 把 JSON 整数
     * 反序列化成 Integer 还是 Long 取决于数值大小，强转会抛
     * ClassCastException（编译期看不出来）。按 Number 转最稳。
     */
    private Integer toInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
