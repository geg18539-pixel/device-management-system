package com.yan.backend.repository;

import com.yan.backend.entity.DeviceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceAttachmentRepository extends JpaRepository<DeviceAttachment, Long> {

    /** 详情页按"最新的在最上面"排列 */
    List<DeviceAttachment> findByDeviceIdOrderByUploadTimeDesc(Long deviceId);

    /**
     * 删除设备时用它列出附件、把磁盘文件一并清掉，避免留下孤儿文件。
     *
     * <p>注意这里**不作为删除保护** —— 附件完全从属于设备，设备没了附件就失去意义，
     * 所以是级联清理；而调拨/维保/维修记录是审计资料，那些才用来阻止删除。
     */
    List<DeviceAttachment> findByDeviceId(Long deviceId);
}
