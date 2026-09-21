package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.common.Result;
import com.yan.backend.common.UserContext;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysMessage;
import com.yan.backend.service.SysMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息。
 *
 * <p><b>刻意不加 @RequirePerm</b>：消息是个人数据，接口内部一律用
 * {@code UserContext} 里的当前用户 id 过滤，天然只能看到自己的。
 * 加权限点反而要多维护一堆"查看自己消息"的权限，没有意义。
 *
 * <p>用户 id **不从请求参数取**，只从登录态取 —— 否则前端传个别人的 id
 * 就能读别人的消息。
 */
@RestController
@RequestMapping("/api/messages")
public class SysMessageController {

    private final SysMessageService messageService;

    public SysMessageController(SysMessageService messageService) {
        this.messageService = messageService;
    }

    /** GET /api/messages/unread-count —— 未读数，顶栏红点用 */
    @GetMapping("/unread-count")
    public ResponseEntity<Result<Long>> unreadCount() {
        return ResponseEntity.ok(Result.success(messageService.countUnread(UserContext.getUserId())));
    }

    /** GET /api/messages/recent?limit=5 —— 最近几条，顶栏下拉用 */
    @GetMapping("/recent")
    public ResponseEntity<Result<List<SysMessage>>> recent(
            @RequestParam(defaultValue = "5") int limit) {
        // 上限兜一下：这个接口是给下拉用的，传个 10000 会把响应撑大
        return ResponseEntity.ok(Result.success(
                messageService.recent(UserContext.getUserId(), Math.min(Math.max(limit, 1), 20))));
    }

    /** GET /api/messages —— 消息中心的分页列表 */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysMessage>>> page(
            @RequestParam(required = false) Boolean readFlag,
            @RequestParam(required = false) String msgType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(messageService.page(
                UserContext.getUserId(), readFlag, msgType, pageNum, pageSize)));
    }

    /** PUT /api/messages/{id}/read —— 标记单条已读 */
    @PutMapping("/{id}/read")
    public ResponseEntity<Result<SysMessage>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success("已标记为已读",
                messageService.markRead(id, UserContext.getUserId())));
    }

    /** PUT /api/messages/read-all —— 全部标记已读 */
    @PutMapping("/read-all")
    public ResponseEntity<Result<Integer>> markAllRead() {
        int count = messageService.markAllRead(UserContext.getUserId());
        return ResponseEntity.ok(Result.success("已全部标记为已读", count));
    }

    /**
     * DELETE /api/messages/read —— 清空已读消息。
     *
     * <p>路径是字面量 "read"，和下面的 {@code /{id}} 不冲突 ——
     * Spring MVC 的路径匹配里字面量优先于变量段。
     */
    @Log(title = "站内消息", businessType = "DELETE")
    @DeleteMapping("/read")
    public ResponseEntity<Result<Integer>> deleteAllRead() {
        int count = messageService.deleteAllRead(UserContext.getUserId());
        return ResponseEntity.ok(Result.success("已清空已读消息", count));
    }

    /** DELETE /api/messages/{id} —— 删除单条 */
    @Log(title = "站内消息", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        messageService.delete(id, UserContext.getUserId());
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
