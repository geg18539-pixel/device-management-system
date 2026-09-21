package com.yan.backend.service;

import com.yan.backend.dto.AttachmentDownloadVO;
import com.yan.backend.entity.DeviceAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DeviceAttachmentService {

    /** 某台设备的附件列表，最新的在前 */
    List<DeviceAttachment> listByDevice(Long deviceId);

    /** 上传附件：先落盘，再写数据库记录 */
    DeviceAttachment upload(Long deviceId, MultipartFile file);

    /**
     * 取附件内容用于下载 / 预览。
     *
     * @param inline true 表示让浏览器内联显示（图片预览用），false 表示走下载
     */
    AttachmentDownloadVO download(Long id, boolean inline);

    /** 删除附件：先删磁盘文件，再删数据库记录 */
    void delete(Long id);
}
