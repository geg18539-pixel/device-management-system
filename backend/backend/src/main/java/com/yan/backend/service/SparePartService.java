package com.yan.backend.service;

import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SparePartStockRequest;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;

import java.util.List;

public interface SparePartService {

    /**
     * 配件分页。
     *
     * @param lowStockOnly true 表示只看库存告急的（库存 ≤ 预警阈值）
     */
    PageResult<SparePart> pageParts(String keyword, String status, Boolean lowStockOnly,
                                    int pageNum, int pageSize);

    SparePart findPart(Long id);

    SparePart createPart(SparePart part);

    /** 修改配件。**库存数量不在这里改**，只能走出入库 */
    SparePart updatePart(Long id, SparePart part);

    /** 删除配件。有出入库流水时拒绝（流水是审计资料） */
    void deletePart(Long id);

    /** 启用状态的配件，给出入库下拉用 */
    List<SparePart> listEnabled();

    /** 库存告急清单 */
    List<SparePart> listLowStock();

    long countLowStock();

    /** 入库 */
    SparePartRecord stockIn(Long partId, SparePartStockRequest request);

    /** 出库。库存不足会被拒绝 */
    SparePartRecord stockOut(Long partId, SparePartStockRequest request);

    /** 出入库流水分页（partId 可选） */
    PageResult<SparePartRecord> pageRecords(Long partId, int pageNum, int pageSize);

    /** 某张维修工单消耗的配件 */
    List<SparePartRecord> listRecordsByRepair(Long repairId);
}
