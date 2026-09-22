package com.yan.backend.repository;

import com.yan.backend.entity.DeviceKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface KnowledgeRepository extends JpaRepository<DeviceKnowledge, Long>,
        JpaSpecificationExecutor<DeviceKnowledge> {

    /** 列表页用：最新的在最上面 */
    List<DeviceKnowledge> findAllByOrderByUploadTimeDesc();

    /** 知识库里某种状态的文档数。后台首页的「处理失败」用它 */
    long countByStatus(String status);
}
