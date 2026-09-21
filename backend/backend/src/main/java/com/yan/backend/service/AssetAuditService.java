package com.yan.backend.service;

import com.yan.backend.dto.AuditLogQuery;
import com.yan.backend.dto.AuditLogVO;
import com.yan.backend.dto.PageResult;

import java.util.List;

/** 资产审计日志的查询。写入由 {@link AssetAuditRecorder} 负责，这里只读 */
public interface AssetAuditService {

    PageResult<AuditLogVO> page(AuditLogQuery query, int pageNum, int pageSize);

    /** 导出用：拿筛选后的全量，不分页 */
    List<AuditLogVO> listForExport(AuditLogQuery query);

    /** 某台设备 / 某个配件的全部变更历史，新的在前 */
    List<AuditLogVO> listByBiz(String bizType, Long bizId);
}
