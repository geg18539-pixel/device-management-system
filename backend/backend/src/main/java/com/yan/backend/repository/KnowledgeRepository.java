package com.yan.backend.repository;

import com.yan.backend.entity.DeviceKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface KnowledgeRepository extends JpaRepository<DeviceKnowledge, Long>,
        JpaSpecificationExecutor<DeviceKnowledge> {

    /** 列表页用：最新的在最上面 */
    List<DeviceKnowledge> findAllByOrderByUploadTimeDesc();
}
