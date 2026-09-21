package com.yan.backend.repository;

import com.yan.backend.entity.AssetAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/**
 * 资产审计日志。
 *
 * <p>继承 {@link JpaSpecificationExecutor}：审计中心的筛选维度有
 * 业务类型 / 动作 / 操作人 / 时间范围 / 关键词 五种，为每种组合写一个
 * findBy 方法不现实，用 Specification 动态拼。
 */
public interface AssetAuditLogRepository extends JpaRepository<AssetAuditLog, Long>,
        JpaSpecificationExecutor<AssetAuditLog> {

    /** 某台设备（或某个配件）的全部变更历史，新的在前。设备详情页用它 */
    List<AssetAuditLog> findByBizTypeAndBizIdOrderByAuditTimeDesc(String bizType, Long bizId);

    /** 审计中心的分页查询 */
    Page<AssetAuditLog> findAll(Pageable pageable);
}
