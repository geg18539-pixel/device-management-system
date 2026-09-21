package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.dto.StoredFileVO;
import com.yan.backend.entity.DeviceAttachment;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceAttachmentRepository;
import com.yan.backend.repository.DeviceRepository;
import com.yan.backend.service.DeviceAttachmentService;
import com.yan.backend.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DeviceAttachmentServiceImpl implements DeviceAttachmentService {

    private final DeviceAttachmentRepository attachmentRepository;
    private final DeviceRepository deviceRepository;
    private final FileStorageService fileStorageService;

    public DeviceAttachmentServiceImpl(DeviceAttachmentRepository attachmentRepository,
                                       DeviceRepository deviceRepository,
                                       FileStorageService fileStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.deviceRepository = deviceRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public List<DeviceAttachment> listByDevice(Long deviceId) {
        return attachmentRepository.findByDeviceIdOrderByUploadTimeDesc(deviceId);
    }

    @Override
    @Transactional
    public DeviceAttachment upload(Long deviceId, MultipartFile file) {
        // 先确认设备存在，避免给不存在的设备挂附件（留下永远查不到的孤儿记录）
        if (!deviceRepository.existsById(deviceId)) {
            throw new ResourceNotFoundException("设备不存在，id = " + deviceId);
        }

        // 顺序很重要：**先落盘、再写库**。
        // 反过来的话，库里有记录但文件没存成功，用户会看到一个点开就报错的附件。
        // 现在这个顺序最坏情况是"磁盘上多了个没人引用的文件"，属于可接受的浪费。
        StoredFileVO stored = fileStorageService.store(file);

        DeviceAttachment attachment = new DeviceAttachment();
        attachment.setDeviceId(deviceId);
        attachment.setFileName(stored.getOriginalName());
        attachment.setStoredName(stored.getStoredName());
        attachment.setContentType(stored.getContentType());
        attachment.setFileSize(stored.getSize());
        attachment.setUploader(uploaderOrAnonymous());

        return attachmentRepository.save(attachment);
    }

    @Override
    public AttachmentDownloadVO download(Long id, boolean inline) {
        DeviceAttachment attachment = getAttachment(id);
        return new AttachmentDownloadVO(
                fileStorageService.load(attachment.getStoredName()),
                attachment.getFileName(),
                resolveContentType(attachment, inline),
                attachment.getFileSize() == null ? 0L : attachment.getFileSize());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        DeviceAttachment attachment = getAttachment(id);

        // 先删库再删文件？还是反过来？
        // 这里选**先删文件**：万一文件删除失败，我们至少还没丢记录，
        // 而且 FileStorageService.delete 内部是"失败也吞掉"的，
        // 不会因为磁盘问题导致用户删不掉附件。
        fileStorageService.delete(attachment.getStoredName());
        attachmentRepository.delete(attachment);
    }

    // ---------------- 私有辅助 ----------------

    private DeviceAttachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("附件不存在，id = " + id));
    }

    private String uploaderOrAnonymous() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }

    /**
     * 决定返回什么 Content-Type。
     *
     * <p>预览时如果是图片就用真实类型（浏览器内联显示）；
     * 其余情况一律降级成 {@code application/octet-stream} —— 这样浏览器
     * **只会下载不会渲染**。上传白名单里已经排除了 html/svg，
     * 这里再降一级，即使以后白名单被放宽也不会直接变成存储型 XSS。
     */
    private String resolveContentType(DeviceAttachment attachment, boolean inline) {
        String contentType = attachment.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        if (inline && isImage) {
            return contentType;
        }
        return "application/octet-stream";
    }
}
