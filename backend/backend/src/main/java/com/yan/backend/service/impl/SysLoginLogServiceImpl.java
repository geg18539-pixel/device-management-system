package com.yan.backend.service.impl;

import com.yan.backend.dto.LoginLogQuery;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysLoginLog;
import com.yan.backend.repository.SysLoginLogRepository;
import com.yan.backend.service.SysLoginLogService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SysLoginLogServiceImpl implements SysLoginLogService {

    private final SysLoginLogRepository sysLoginLogRepository;

    public SysLoginLogServiceImpl(SysLoginLogRepository sysLoginLogRepository) {
        this.sysLoginLogRepository = sysLoginLogRepository;
    }

    @Override
    public PageResult<SysLoginLog> page(LoginLogQuery query, int pageNum, int pageSize) {
        // 登录日志永远是"最新的在最上面"，没提供排序选项 ——
        // 看这个页面的人基本都是在追查最近发生了什么
        Pageable pageable = PageRequest.of(Math.max(pageNum, 1) - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "loginTime"));

        Page<SysLoginLog> page = sysLoginLogRepository.findAll(buildSpec(query), pageable);
        return PageResult.of(page);
    }

    private Specification<SysLoginLog> buildSpec(LoginLogQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.getUsername())) {
                predicates.add(cb.like(root.get("username"),
                        "%" + query.getUsername().trim() + "%"));
            }

            if (StringUtils.hasText(query.getIp())) {
                predicates.add(cb.like(root.get("ip"), "%" + query.getIp().trim() + "%"));
            }

            if (query.getSuccess() != null) {
                predicates.add(cb.equal(root.get("success"), query.getSuccess()));
            }

            if (query.getLoginTimeBegin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("loginTime"),
                        query.getLoginTimeBegin().atStartOfDay()));
            }

            if (query.getLoginTimeEnd() != null) {
                // 和用户列表同样的坑：结束日期要取当天最后一刻，
                // 否则"查到 9 月 19 日"会把 19 号当天的日志全部漏掉
                predicates.add(cb.lessThanOrEqualTo(root.get("loginTime"),
                        query.getLoginTimeEnd().atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
