package com.yan.backend.controller;

import com.yan.backend.common.Result;
import com.yan.backend.dto.QueryAskRequest;
import com.yan.backend.dto.QueryCatalogVO;
import com.yan.backend.dto.QueryResultVO;
import com.yan.backend.service.DataQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自然语言问数。
 *
 * <p><b>刻意只要求登录，不加 @RequirePerm</b> —— 和看板、AI 助手、故障诊断同一个口径。
 * 问数能查到的数据（设备、工单、配件）本来就在那些页面上对所有登录用户可见，
 * 这里没有暴露任何新东西；而它恰恰是"给运维同事自己看数"用的，
 * 加权限等于把功能锁给管理员。
 *
 * <p>真正要守的是**不碰系统数据**：查询目录里只有设备、工单、配件三张表，
 * 用户/角色/菜单一律不在其中。这一条在 {@link com.yan.backend.ai.query.QueryCatalog}
 * 里写死了，不是靠这里少写几个注解。
 *
 * <p>这就是"受控问数"和"自由 Text2SQL"在权限设计上最大的区别：
 * 后者的数据边界要靠账号和表名白名单在运行时判断，
 * 而前者**在编译期就只有那几条查询存在**。
 *
 * <p>不加 {@code @Log}：这是只读查询，不改任何业务表，记进操作日志只会给审计添噪音。
 */
@RestController
@RequestMapping("/api/query")
public class DataQueryController {

    private final DataQueryService dataQueryService;

    public DataQueryController(DataQueryService dataQueryService) {
        this.dataQueryService = dataQueryService;
    }

    /**
     * GET /api/query/catalog —— 能问什么（指标说明 + 示例问题）。
     *
     * <p>页面加载时拉一次，用来渲染提示区。内容从目录里生成，
     * 所以不会出现"页面上写的比实际能做的少一条"。
     */
    @GetMapping("/catalog")
    public ResponseEntity<Result<QueryCatalogVO>> catalog() {
        return ResponseEntity.ok(Result.success(dataQueryService.catalog()));
    }

    /**
     * POST /api/query/ask —— 问一句。
     *
     * <p>「没理解你的问题」返回 **400 + 一句可操作的提示**（说明能问什么），
     * 不是 500 —— 这是使用中最常见的失败，把它显示成"服务器内部错误"
     * 会让人以为系统坏了。
     */
    @PostMapping("/ask")
    public ResponseEntity<Result<QueryResultVO>> ask(@Valid @RequestBody QueryAskRequest request) {
        return ResponseEntity.ok(Result.success(dataQueryService.ask(request.question())));
    }
}
