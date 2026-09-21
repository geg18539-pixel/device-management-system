package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.DownloadUtils;
import com.yan.backend.common.Result;
import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.entity.DeviceAttachment;
import com.yan.backend.service.DeviceAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * 设备附件：上传 / 列表 / 下载 / 删除。
 *
 * <p><b>为什么下载也要走接口、不直接暴露静态目录？</b>
 * 附件是设备说明书、验收单这类内部资料，不该让拿到 URL 的人就能看。
 * 走接口意味着它天然被 JwtInterceptor 挡住（需要登录）。
 * 代价是前端不能用 {@code <img src>} 直接显示图片 ——
 * 那种方式带不了 Authorization 头。所以图片预览在前端是
 * 先用 axios 取回二进制、再转成 blob URL 给 img 用的。
 *
 * <p>也没有做"把 token 放 query 参数里"那种折中：那会把 token 写进
 * 服务器访问日志和 Referer，得不偿失。
 */
@RestController
public class DeviceAttachmentController {

    private final DeviceAttachmentService attachmentService;

    public DeviceAttachmentController(DeviceAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** GET /api/devices/{deviceId}/attachments —— 某台设备的附件列表 */
    @RequirePerm("dev:device:list")
    @GetMapping("/api/devices/{deviceId}/attachments")
    public ResponseEntity<Result<List<DeviceAttachment>>> list(@PathVariable Long deviceId) {
        return ResponseEntity.ok(Result.success(attachmentService.listByDevice(deviceId)));
    }

    /**
     * POST /api/devices/{deviceId}/attachments —— 上传附件。
     *
     * <p>用 multipart/form-data，字段名固定为 {@code file}。
     */
    @Log(title = "设备附件", businessType = "INSERT")
    @RequirePerm("dev:attachment:upload")
    @PostMapping("/api/devices/{deviceId}/attachments")
    public ResponseEntity<Result<DeviceAttachment>> upload(
            @PathVariable Long deviceId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("上传成功", attachmentService.upload(deviceId, file)));
    }

    /**
     * GET /api/device-attachments/{id}/download —— 下载或预览。
     *
     * <p>{@code inline=true} 时图片会内联显示（缩略图预览用），
     * 其余情况一律按附件下载。
     */
    @GetMapping("/api/device-attachments/{id}/download")
    @RequirePerm("dev:device:list")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @RequestParam(name = "inline", defaultValue = "false") boolean inline) {

        AttachmentDownloadVO vo = attachmentService.download(id, inline);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                DownloadUtils.contentDisposition(vo.getFileName(), inline));
        headers.setContentLength(vo.getSize());
        // 让浏览器按我们给的 Content-Type 处理，不要去猜（猜错可能把文件当页面渲染）
        headers.set(HttpHeaders.CONTENT_TYPE, vo.getContentType());
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");

        return ResponseEntity.ok()
                .headers(headers)
                .body(vo.getResource());
    }

    /** DELETE /api/device-attachments/{id} —— 删除附件（连同磁盘文件） */
    @Log(title = "设备附件", businessType = "DELETE")
    @RequirePerm("dev:attachment:remove")
    @DeleteMapping("/api/device-attachments/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }
}
