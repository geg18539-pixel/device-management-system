package com.yan.backend.repository;

import com.yan.backend.entity.RepairAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepairAttachmentRepository extends JpaRepository<RepairAttachment, Long> {

    /** 某张工单的附件，最新的在前 */
    List<RepairAttachment> findByRepairIdOrderByUploadTimeDesc(Long repairId);

    /** 删除工单时用它列出附件、把磁盘文件一并清掉 */
    List<RepairAttachment> findByRepairId(Long repairId);
}
