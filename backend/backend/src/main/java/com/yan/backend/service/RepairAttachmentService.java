package com.yan.backend.service;

import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.entity.RepairAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RepairAttachmentService {

    List<RepairAttachment> listByRepair(Long repairId);

    RepairAttachment upload(Long repairId, MultipartFile file);

    /** @param inline true 表示图片内联预览 */
    AttachmentDownloadVO download(Long id, boolean inline);

    void delete(Long id);
}
