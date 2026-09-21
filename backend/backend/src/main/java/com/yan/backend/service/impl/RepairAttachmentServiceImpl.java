package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.dto.StoredFileVO;
import com.yan.backend.entity.RepairAttachment;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.RepairAttachmentRepository;
import com.yan.backend.service.FileStorageService;
import com.yan.backend.service.RepairAttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RepairAttachmentServiceImpl implements RepairAttachmentService {

    private final RepairAttachmentRepository attachmentRepository;
    private final DeviceRepairRepository repairRepository;
    private final FileStorageService fileStorageService;

    public RepairAttachmentServiceImpl(RepairAttachmentRepository attachmentRepository,
                                       DeviceRepairRepository repairRepository,
                                       FileStorageService fileStorageService) {
        this.attachmentRepository = attachmentRepository;
        this.repairRepository = repairRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public List<RepairAttachment> listByRepair(Long repairId) {
        return attachmentRepository.findByRepairIdOrderByUploadTimeDesc(repairId);
    }

    @Override
    @Transactional
    public RepairAttachment upload(Long repairId, MultipartFile file) {
        // 先确认工单存在，避免给不存在的工单挂附件（留下永远查不到的孤儿记录）
        if (!repairRepository.existsById(repairId)) {
            throw new ResourceNotFoundException("维修工单不存在，id = " + repairId);
        }

        // 先落盘再写库（和 DeviceAttachment 一致）：最坏情况是磁盘上多个没人引用的文件，
        // 反过来则是"库里有记录但文件不存在"，用户点开就报错
        StoredFileVO stored = fileStorageService.store(file);

        RepairAttachment attachment = new RepairAttachment();
        attachment.setRepairId(repairId);
        attachment.setFileName(stored.getOriginalName());
        attachment.setStoredName(stored.getStoredName());
        attachment.setContentType(stored.getContentType());
        attachment.setFileSize(stored.getSize());
        attachment.setUploader(uploaderOrAnonymous());

        return attachmentRepository.save(attachment);
    }

    @Override
    public AttachmentDownloadVO download(Long id, boolean inline) {
        RepairAttachment attachment = getAttachment(id);
        return new AttachmentDownloadVO(
                fileStorageService.load(attachment.getStoredName()),
                attachment.getFileName(),
                resolveContentType(attachment, inline),
                attachment.getFileSize() == null ? 0L : attachment.getFileSize());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RepairAttachment attachment = getAttachment(id);
        // 先删文件再删记录：文件删除失败也不会导致用户删不掉附件
        fileStorageService.delete(attachment.getStoredName());
        attachmentRepository.delete(attachment);
    }

    // ---------------- 私有辅助 ----------------

    private RepairAttachment getAttachment(Long id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("附件不存在，id = " + id));
    }

    private String uploaderOrAnonymous() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }

    /**
     * 只有图片预览才用真实类型，其余一律降级成 octet-stream 让浏览器只下载不渲染。
     * 上传白名单里已经排除了 html/svg，这里是第二道保险。
     */
    private String resolveContentType(RepairAttachment attachment, boolean inline) {
        String contentType = attachment.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        if (inline && isImage) {
            return contentType;
        }
        return "application/octet-stream";
    }
}
