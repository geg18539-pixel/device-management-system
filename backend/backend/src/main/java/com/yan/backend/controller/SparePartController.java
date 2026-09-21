package com.yan.backend.controller;

import com.yan.backend.annotation.Log;
import com.yan.backend.annotation.RequirePerm;
import com.yan.backend.common.Result;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SparePartStockRequest;
import com.yan.backend.entity.SparePart;
import com.yan.backend.entity.SparePartRecord;
import com.yan.backend.service.SparePartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配件 / 耗材管理。
 *
 * <p>库存只能通过 {@code /stock-in} 和 {@code /stock-out} 变动，
 * 新增和编辑接口都不接受库存数字（Service 里显式忽略），
 * 保证"当前库存 = 历次流水累加"这个恒等式成立。
 */
@RequirePerm("dev:part:list")
@RestController
@RequestMapping("/api/spare-parts")
public class SparePartController {

    private final SparePartService sparePartService;

    public SparePartController(SparePartService sparePartService) {
        this.sparePartService = sparePartService;
    }

    // ---------------- 配件主数据 ----------------

    /** GET /api/spare-parts —— 分页查询 */
    @GetMapping
    public ResponseEntity<Result<PageResult<SparePart>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(
                sparePartService.pageParts(keyword, status, lowStockOnly, pageNum, pageSize)));
    }

    /** GET /api/spare-parts/options —— 启用中的配件（下拉用） */
    @GetMapping("/options")
    public ResponseEntity<Result<List<SparePart>>> options() {
        return ResponseEntity.ok(Result.success(sparePartService.listEnabled()));
    }

    /** GET /api/spare-parts/low-stock —— 库存告急清单 */
    @GetMapping("/low-stock")
    public ResponseEntity<Result<List<SparePart>>> lowStock() {
        return ResponseEntity.ok(Result.success(sparePartService.listLowStock()));
    }

    /** GET /api/spare-parts/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Result<SparePart>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(sparePartService.findPart(id)));
    }

    /** POST /api/spare-parts —— 新增配件（库存从 0 开始，之后走入库） */
    @Log(title = "配件管理", businessType = "INSERT")
    @RequirePerm("dev:part:add")
    @PostMapping
    public ResponseEntity<Result<SparePart>> create(@Valid @RequestBody SparePart part) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("新增成功", sparePartService.createPart(part)));
    }

    /** PUT /api/spare-parts/{id} —— 编辑（不包含库存） */
    @Log(title = "配件管理", businessType = "UPDATE")
    @RequirePerm("dev:part:edit")
    @PutMapping("/{id}")
    public ResponseEntity<Result<SparePart>> update(@PathVariable Long id,
                                                    @Valid @RequestBody SparePart part) {
        return ResponseEntity.ok(Result.success("修改成功", sparePartService.updatePart(id, part)));
    }

    /** DELETE /api/spare-parts/{id} —— 删除（有流水时会被拒绝） */
    @Log(title = "配件管理", businessType = "DELETE")
    @RequirePerm("dev:part:remove")
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long id) {
        sparePartService.deletePart(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    // ---------------- 出入库 ----------------

    /** POST /api/spare-parts/{id}/stock-in —— 入库 */
    @Log(title = "配件入库", businessType = "UPDATE")
    @RequirePerm("dev:part:stock")
    @PostMapping("/{id}/stock-in")
    public ResponseEntity<Result<SparePartRecord>> stockIn(
            @PathVariable Long id, @Valid @RequestBody SparePartStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("入库成功", sparePartService.stockIn(id, request)));
    }

    /**
     * POST /api/spare-parts/{id}/stock-out —— 出库。
     *
     * <p>可以在请求里带 {@code relatedRepairId} 关联到某张维修工单，
     * 这样设备详情页就能看到"这台设备换过哪些配件"。
     */
    @Log(title = "配件出库", businessType = "UPDATE")
    @RequirePerm("dev:part:stock")
    @PostMapping("/{id}/stock-out")
    public ResponseEntity<Result<SparePartRecord>> stockOut(
            @PathVariable Long id, @Valid @RequestBody SparePartStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("出库成功", sparePartService.stockOut(id, request)));
    }

    // ---------------- 流水 ----------------

    /** GET /api/spare-parts/records —— 出入库流水（partId 可选） */
    @GetMapping("/records")
    public ResponseEntity<Result<PageResult<SparePartRecord>>> records(
            @RequestParam(required = false) Long partId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {

        return ResponseEntity.ok(Result.success(
                sparePartService.pageRecords(partId, pageNum, pageSize)));
    }

    /** GET /api/spare-parts/records/by-repair/{repairId} —— 某张工单消耗的配件 */
    @GetMapping("/records/by-repair/{repairId}")
    public ResponseEntity<Result<List<SparePartRecord>>> recordsByRepair(
            @PathVariable Long repairId) {
        return ResponseEntity.ok(Result.success(sparePartService.listRecordsByRepair(repairId)));
    }
}
