package com.yan.backend.service.impl;

import com.yan.backend.common.AuditActions;
import com.yan.backend.dto.AuditFieldChange;
import com.yan.backend.dto.AuditLogQuery;
import com.yan.backend.dto.AuditLogVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.repository.AssetAuditLogRepository;
import com.yan.backend.service.AssetAuditService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AssetAuditServiceImpl implements AssetAuditService {

    /** 导出的行数上限。审计表会一直涨，不设上限迟早导出超时 */
    private static final int EXPORT_LIMIT = 10_000;

    private final AssetAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AssetAuditServiceImpl(AssetAuditLogRepository auditLogRepository,
                                 ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AuditLogVO> page(AuditLogQuery query, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(pageNum, 1) - 1, pageSize, defaultSort());
        Page<AssetAuditLog> page = auditLogRepository.findAll(buildSpec(query), pageable);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public List<AuditLogVO> listForExport(AuditLogQuery query) {
        Pageable pageable = PageRequest.of(0, EXPORT_LIMIT, defaultSort());
        return auditLogRepository.findAll(buildSpec(query), pageable)
                .getContent().stream().map(this::toVO).toList();
    }

    @Override
    public List<AuditLogVO> listByBiz(String bizType, Long bizId) {
        return auditLogRepository
                .findByBizTypeAndBizIdOrderByAuditTimeDesc(bizType, bizId)
                .stream().map(this::toVO).toList();
    }

    /**
     * 审计永远是"最新的在最上面"，不提供排序选项 ——
     * 看这个页面的人基本都在追查刚刚发生了什么。
     *
     * <p>⚠️ 必须**带上 id 做第二排序键**：同一秒内的多条记录（比如一次批量操作）
     * auditTime 完全相同，只按它排序时数据库返回的顺序是不确定的，
     * 分页就会出现"第 2 页漏掉一条、第 3 页又冒出来"的重复/丢失。
     * 加 id 之后顺序是唯一确定的。
     */
    private Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "auditTime")
                .and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private Specification<AssetAuditLog> buildSpec(AuditLogQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(query.getBizType())) {
                predicates.add(cb.equal(root.get("bizType"), query.getBizType().trim()));
            }
            if (query.getBizId() != null) {
                predicates.add(cb.equal(root.get("bizId"), query.getBizId()));
            }
            if (StringUtils.hasText(query.getAction())) {
                predicates.add(cb.equal(root.get("action"), query.getAction().trim()));
            }
            if (StringUtils.hasText(query.getOperator())) {
                predicates.add(cb.like(root.get("operator"),
                        "%" + query.getOperator().trim() + "%"));
            }

            if (StringUtils.hasText(query.getKeyword())) {
                String like = "%" + query.getKeyword().trim() + "%";
                // 编号 / 名称 / 操作人昵称任一命中即可 —— 用户手上可能只有一个编号，
                // 也可能只记得是谁改的
                predicates.add(cb.or(
                        cb.like(root.get("bizCode"), like),
                        cb.like(root.get("bizName"), like),
                        cb.like(root.get("operatorName"), like)));
            }

            if (query.getAuditTimeBegin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("auditTime"),
                        query.getAuditTimeBegin().atStartOfDay()));
            }
            if (query.getAuditTimeEnd() != null) {
                // 结束日期取当天最后一刻。用 atStartOfDay 的话
                // "查到 9 月 21 日"会把 21 号当天的记录全部漏掉
                predicates.add(cb.lessThanOrEqualTo(root.get("auditTime"),
                        query.getAuditTimeEnd().atTime(LocalTime.MAX)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogVO toVO(AssetAuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setBizType(entity.getBizType());
        vo.setBizTypeLabel(AuditActions.bizTypeLabelOf(entity.getBizType()));
        vo.setBizId(entity.getBizId());
        vo.setBizName(entity.getBizName());
        vo.setBizCode(entity.getBizCode());
        vo.setAction(entity.getAction());
        vo.setActionLabel(AuditActions.labelOf(entity.getAction()));
        vo.setChanges(parseChanges(entity.getChanges()));
        vo.setChangeCount(entity.getChangeCount());
        vo.setOperator(entity.getOperator());
        vo.setOperatorName(entity.getOperatorName());
        vo.setRemark(entity.getRemark());
        vo.setAuditTime(entity.getAuditTime());
        return vo;
    }

    /**
     * 把 changes 那列 JSON 解成结构化列表。
     *
     * <p>解析失败时**返回空列表而不是抛异常**：审计记录是历史资料，
     * 一条记录的明细格式有问题，不该让整个审计页面打不开 ——
     * 那等于把"看不了历史"当成了处理手段。列表页仍然能显示
     * "谁在什么时候改了什么动作"这些最关键的字段。
     */
    private List<AuditFieldChange> parseChanges(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<AuditFieldChange> list = new ArrayList<>();
            for (JsonNode item : root) {
                list.add(new AuditFieldChange(
                        item.path("field").asString(""),
                        item.path("before").asString(""),
                        item.path("after").asString("")));
            }
            return list;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
