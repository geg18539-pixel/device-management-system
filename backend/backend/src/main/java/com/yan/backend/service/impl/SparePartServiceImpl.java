package com.yan.backend.service.impl;

import com.yan.backend.common.UserContext;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SparePartStockRequest;
import com.yan.backend.entity.AssetAuditLog;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;
import com.yan.backend.exception.ResourceNotFoundException;
import com.yan.backend.repository.DeviceRepairRepository;
import com.yan.backend.repository.SparePartRecordRepository;
import com.yan.backend.repository.SparePartRepository;
import com.yan.backend.service.AssetAuditRecorder;
import com.yan.backend.service.SparePartService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SparePartServiceImpl implements SparePartService {

    private final SparePartRepository partRepository;
    private final SparePartRecordRepository recordRepository;
    private final DeviceRepairRepository repairRepository;
    private final AssetAuditRecorder auditRecorder;

    public SparePartServiceImpl(SparePartRepository partRepository,
                                SparePartRecordRepository recordRepository,
                                DeviceRepairRepository repairRepository,
                                AssetAuditRecorder auditRecorder) {
        this.partRepository = partRepository;
        this.recordRepository = recordRepository;
        this.repairRepository = repairRepository;
        this.auditRecorder = auditRecorder;
    }

    // ============================================================
    // 配件主数据
    // ============================================================

    @Override
    public PageResult<SparePart> pageParts(String keyword, String status, Boolean lowStockOnly,
                                           int pageNum, int pageSize) {
        Specification<SparePart> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("partCode"), like),
                        cb.like(root.get("partName"), like),
                        cb.like(root.get("model"), like),
                        cb.like(root.get("supplier"), like)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            }
            if (Boolean.TRUE.equals(lowStockOnly)) {
                // 两个都是本表的列，直接比较即可。
                // 注意是 le（小于等于）：库存等于阈值就该预警了
                predicates.add(cb.le(root.get("stockQuantity"), root.get("warnThreshold")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                Math.max(pageNum, 1) - 1, pageSize, Sort.by(Sort.Direction.ASC, "partCode"));
        return PageResult.of(partRepository.findAll(spec, pageable));
    }

    @Override
    public SparePart findPart(Long id) {
        return getPart(id);
    }

    @Override
    @Transactional
    public SparePart createPart(SparePart part) {
        part.setId(null);
        if (partRepository.existsByPartCode(part.getPartCode())) {
            throw new IllegalStateException("配件编码已存在：" + part.getPartCode());
        }
        // 新建时库存一律从 0 开始，**不接受请求里传的库存数字**。
        // 想让库存变成 20，正确做法是建完之后走一次入库 ——
        // 那样既能留下流水，也能记录是谁入的、什么时候入的
        part.setStockQuantity(0);
        if (part.getWarnThreshold() == null) {
            part.setWarnThreshold(0);
        }
        if (!StringUtils.hasText(part.getStatus())) {
            part.setStatus(SparePart.STATUS_ENABLED);
        }
        SparePart saved = partRepository.save(part);
        // 建档进审计：初始编码/名称/阈值是后续对账的基准
        auditRecorder.recordPartCreate(saved);
        return saved;
    }

    @Override
    @Transactional
    public SparePart updatePart(Long id, SparePart part) {
        SparePart existing = getPart(id);

        var auditDraft = auditRecorder.draftForPart(existing, AssetAuditLog.ACTION_UPDATE);

        if (!existing.getPartCode().equals(part.getPartCode())
                && partRepository.existsByPartCode(part.getPartCode())) {
            throw new IllegalStateException("配件编码已存在：" + part.getPartCode());
        }

        existing.setPartCode(part.getPartCode());
        existing.setPartName(part.getPartName());
        existing.setModel(part.getModel());
        existing.setUnit(part.getUnit());
        existing.setUnitPrice(part.getUnitPrice());
        existing.setSupplier(part.getSupplier());
        existing.setLocation(part.getLocation());
        existing.setRemark(part.getRemark());
        if (part.getWarnThreshold() != null) {
            existing.setWarnThreshold(part.getWarnThreshold());
        }
        if (StringUtils.hasText(part.getStatus())) {
            existing.setStatus(part.getStatus());
        }

        // ★ 刻意**不接收**请求里的 stockQuantity。
        // 允许直接改库存的话，账实不符就查不出来了 ——
        // 库存只能通过出入库流水变动，"现在有多少"永远等于历次流水累加
        SparePart saved = partRepository.save(existing);
        auditRecorder.commit(auditDraft);
        return saved;
    }

    @Override
    @Transactional
    public void deletePart(Long id) {
        SparePart part = getPart(id);

        // 有流水的配件不能删：流水里的 part_id 会变成悬空引用，
        // 而且"这个件历史上入过多少出过多少"是资产追溯资料。
        // 不再使用的配件请改为「停用」
        if (recordRepository.countByPartId(id) > 0) {
            throw new IllegalStateException("该配件已有出入库流水，不能删除（不再使用请改为「停用」）");
        }

        var auditDraft = auditRecorder.draftForPart(part, AssetAuditLog.ACTION_DELETE);
        partRepository.delete(part);
        // 删除没有字段变化，commit 对 DELETE 会强制记一条。
        // 能走到这里说明它本来就没有流水，删掉之后审计里至少还留着它的编码和名称
        auditRecorder.commit(auditDraft);
    }

    @Override
    public List<SparePart> listEnabled() {
        return partRepository.findAll().stream()
                .filter(p -> SparePart.STATUS_ENABLED.equals(p.getStatus()))
                .sorted((a, b) -> a.getPartCode().compareTo(b.getPartCode()))
                .toList();
    }

    @Override
    public List<SparePart> listLowStock() {
        return partRepository.findLowStock(SparePart.STATUS_ENABLED);
    }

    @Override
    public long countLowStock() {
        return partRepository.countLowStock(SparePart.STATUS_ENABLED);
    }

    // ============================================================
    // 出入库
    // ============================================================

    @Override
    @Transactional
    public SparePartRecord stockIn(Long partId, SparePartStockRequest request) {
        // ★ 必须用带悲观写锁的查询，理由见 SparePartRepository.findByIdForUpdate
        SparePart part = getPartForUpdate(partId);

        int before = part.getStockQuantity() == null ? 0 : part.getStockQuantity();

        // 快照要在**拿到写锁之后**拍：锁之前读到的库存可能已经被
        // 另一个并发请求改掉了，那样审计里记的"变更前"是错的
        var auditDraft = auditRecorder.draftForPart(part, AssetAuditLog.ACTION_STOCK_IN);

        int after = before + request.getQuantity();

        part.setStockQuantity(after);
        partRepository.save(part);

        SparePartRecord record = writeRecord(part, SparePartRecord.TYPE_IN, request, before, after);
        auditRecorder.commit(auditDraft, stockRemark("入库", request, part));
        return record;
    }

    @Override
    @Transactional
    public SparePartRecord stockOut(Long partId, SparePartStockRequest request) {
        SparePart part = getPartForUpdate(partId);

        int before = part.getStockQuantity() == null ? 0 : part.getStockQuantity();
        int quantity = request.getQuantity();

        // 库存不足直接拒绝，**不允许扣成负数**。
        // 负库存在账面上毫无意义（东西已经出去了但账上是-3），
        // 而且会掩盖"该补货了"这个信号
        if (before < quantity) {
            throw new IllegalStateException("库存不足：「" + part.getPartName()
                    + "」当前库存 " + before + "，本次需要 " + quantity);
        }

        // 出库如果挂了工单，先确认工单真实存在。
        // 不校验的话，前端传错 id 会在流水里留下一个指向不存在工单的引用，
        // 详情页反查时那条记录就永远显示不出来
        if (request.getRelatedRepairId() != null
                && !repairRepository.existsById(request.getRelatedRepairId())) {
            throw new IllegalArgumentException("关联的维修工单不存在");
        }

        int after = before - quantity;

        var auditDraft = auditRecorder.draftForPart(part, AssetAuditLog.ACTION_STOCK_OUT);

        part.setStockQuantity(after);
        partRepository.save(part);

        SparePartRecord record = writeRecord(part, SparePartRecord.TYPE_OUT, request, before, after);
        auditRecorder.commit(auditDraft, stockRemark("出库", request, part));
        return record;
    }

    @Override
    public PageResult<SparePartRecord> pageRecords(Long partId, int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(pageNum, 1) - 1, pageSize);

        if (partId != null) {
            return PageResult.of(recordRepository.findByPartIdOrderByRecordTimeDesc(partId, pageable));
        }
        return PageResult.of(recordRepository.findAllByOrderByRecordTimeDesc(pageable));
    }

    @Override
    public List<SparePartRecord> listRecordsByRepair(Long repairId) {
        return recordRepository.findByRelatedRepairIdOrderByRecordTimeDesc(repairId);
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private SparePart getPart(Long id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("配件不存在，id = " + id));
    }

    private SparePart getPartForUpdate(Long id) {
        return partRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("配件不存在，id = " + id));
    }

    /** 写一条流水。前后库存快照必须如实记录，这是流水能独立对账的前提 */
    private SparePartRecord writeRecord(SparePart part, String type,
                                        SparePartStockRequest request,
                                        int before, int after) {
        SparePartRecord record = new SparePartRecord();
        record.setPartId(part.getId());
        // 存编码和名称的快照：配件以后改名了，历史流水也该显示当时的名字
        record.setPartCode(part.getPartCode());
        record.setPartName(part.getPartName());
        record.setRecordType(type);
        record.setQuantity(request.getQuantity());
        record.setBeforeStock(before);
        record.setAfterStock(after);
        record.setRelatedRepairId(request.getRelatedRepairId());
        // 单价不填时取配件的参考单价（出库估算成本用）
        record.setUnitPrice(request.getUnitPrice() != null
                ? request.getUnitPrice() : part.getUnitPrice());
        record.setOperator(currentOperator());
        record.setRecordTime(LocalDateTime.now());
        record.setRemark(request.getRemark());
        return recordRepository.save(record);
    }

    private String currentOperator() {
        String username = UserContext.getUsername();
        return StringUtils.hasText(username) ? username : "未知用户";
    }

    /**
     * 出入库的备注文案。
     *
     * <p>审计里「当前库存 5 → 8」已经说明了变动结果，但说明不了**为什么**：
     * 入了多少、挂在哪张工单上、操作人写了什么原因。
     * 这些信息只存在于请求里，不拼进备注就丢了。
     */
    private String stockRemark(String action, SparePartStockRequest request, SparePart part) {
        StringBuilder sb = new StringBuilder();
        sb.append(action).append(' ').append(request.getQuantity());
        if (StringUtils.hasText(part.getUnit())) {
            sb.append(' ').append(part.getUnit());
        }
        if (request.getRelatedRepairId() != null) {
            sb.append("，关联维修工单 #").append(request.getRelatedRepairId());
        }
        if (StringUtils.hasText(request.getRemark())) {
            sb.append("，").append(request.getRemark());
        }
        return sb.toString();
    }
}
