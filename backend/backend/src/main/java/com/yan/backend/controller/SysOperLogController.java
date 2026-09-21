package com.yan.backend.controller;

import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.annotation.RequireRole;
import com.yan.backend.common.Result;
import com.yan.backend.dto.OperLogQuery;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysOperLog;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.SysOperLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志（审计日志）查询。
 *
 * <p><b>只读</b>：没有修改和删除接口。日志由 {@code LogAspect} 在业务方法执行后
 * 异步写入，事后不可篡改是审计资料的基本要求。
 *
 * <p>查询接口本身**不写日志** —— 否则每看一次日志就多一条日志，
 * 用不了多久这张表里就全是"查询操作日志"的记录，把真正的业务操作淹没了。
 */
@RequireRole("admin")
@RequirePerm("sys:operlog:list")
@RestController
@RequestMapping("/api/system/oper-logs")
public class SysOperLogController {

    private final SysOperLogRepository operLogRepository;

    public SysOperLogController(SysOperLogRepository operLogRepository) {
        this.operLogRepository = operLogRepository;
    }

    /** GET /api/system/oper-logs —— 分页 + 条件筛选，默认按时间倒序 */
    @GetMapping
    public ResponseEntity<Result<PageResult<SysOperLog>>> page(OperLogQuery query) {
        Pageable pageable = PageRequest.of(
                Math.max(query.getPageNum(), 1) - 1,
                Math.max(query.getPageSize(), 1),
                // 固定按时间倒序，不提供排序选项：
                // 看这页的人都在追查"最近发生了什么"，
                // 允许改排序只会让人不小心切到正序然后以为日志没记上
                Sort.by(Sort.Direction.DESC, "operTime", "id"));

        return ResponseEntity.ok(Result.success(
                PageResult.of(operLogRepository.findAll(buildSpec(query), pageable))));
    }

    /** GET /api/system/oper-logs/{id} —— 单条详情（含失败原因） */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SysOperLog>> detail(@PathVariable Long id) {
        SysOperLog log = operLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("操作日志不存在，id = " + id));
        return ResponseEntity.ok(Result.success(log));
    }

    private Specification<SysOperLog> buildSpec(OperLogQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.getOperatorName())) {
                predicates.add(cb.like(root.get("operatorName"),
                        "%" + query.getOperatorName().trim() + "%"));
            }
            if (StringUtils.hasText(query.getTitle())) {
                predicates.add(cb.like(root.get("title"), "%" + query.getTitle().trim() + "%"));
            }
            if (StringUtils.hasText(query.getBusinessType())) {
                predicates.add(cb.equal(root.get("businessType"), query.getBusinessType().trim()));
            }
            if (StringUtils.hasText(query.getStatus())) {
                predicates.add(cb.equal(root.get("status"), query.getStatus().trim()));
            }
            if (query.getBeginDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operTime"),
                        query.getBeginDate().atStartOfDay()));
            }
            if (query.getEndDate() != null) {
                // ★ 结束日期必须取当天最后一刻。用 atStartOfDay 的话，
                // "查到 9 月 20 日"会把 9 月 20 日当天的操作全部漏掉 ——
                // 日期范围查询最经典的 off-by-one，用户会以为"今天的操作没记上"
                predicates.add(cb.lessThanOrEqualTo(root.get("operTime"),
                        query.getEndDate().atTime(LocalTime.MAX)));
            }
            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("requestUrl"), like),
                        cb.like(root.get("method"), like)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
