package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.DownloadUtils;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.entity.RepairAttachment;
import com.yan.backend.service.RepairAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 维修工单附件（现场照片、更换件照片、检测报告）。
 *
 * <p>和 {@code DeviceAttachmentController} 一样，下载**必须走接口**而不是静态目录：
 * 附件是内部资料，走接口才能被 JwtInterceptor 挡住。
 * 代价是前端不能用 {@code <img src>} 直接显示（带不了 Authorization 头），
 * 所以图片预览得先取二进制、再转 blob URL。
 */
@RestController
public class RepairAttachmentController {

    private final RepairAttachmentService attachmentService;

    public RepairAttachmentController(RepairAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** GET /api/device-repairs/{repairId}/attachments */
    @RequirePerm("dev:repair:list")
    @GetMapping("/api/device-repairs/{repairId}/attachments")
    public ResponseEntity<Result<List<RepairAttachment>>> list(@PathVariable Long repairId) {
        return ResponseEntity.ok(Result.success(attachmentService.listByRepair(repairId)));
    }

    /** POST /api/device-repairs/{repairId}/attachments —— 上传（multipart，字段名 file） */
    @Log(title = "维修工单附件", businessType = "INSERT")
    @RequirePerm("dev:attachment:upload")
    @PostMapping("/api/device-repairs/{repairId}/attachments")
    public ResponseEntity<Result<RepairAttachment>> upload(
            @PathVariable Long repairId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("上传成功", attachmentService.upload(repairId, file)));
    }

    /** GET /api/repair-attachments/{id}/download —— 下载或图片预览（inline=true） */
    @RequirePerm("dev:repair:list")
    @GetMapping("/api/repair-attachments/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline) {

        AttachmentDownloadVO vo = attachmentService.download(id, inline);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition(vo.getFileName(), inline));
        headers.setContentLength(vo.getSize());
        headers.set(HttpHeaders.CONTENT_TYPE, vo.getContentType());
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");

        return ResponseEntity.ok().headers(headers).body(vo.getResource());
    }

    /** DELETE /api/repair-attachments/{id} */
    @Log(title = "维修工单附件", businessType = "DELETE")
    @RequirePerm("dev:attachment:remove")
    @DeleteMapping("/api/repair-attachments/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
