package com.yan.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 配件出入库请求。
 *
 * <p>数量**始终是正数**，方向由调用的接口决定（/stock-in 还是 /stock-out）。
 * 用"数量带正负号"表示方向看着省事，但很容易出问题：
 * 出库接口收到 -5 到底该扣 5 还是加 5？前端传错一个符号就变成反方向操作。
 */
public class SparePartStockRequest {

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    private Integer quantity;

    /**
     * 关联的维修工单 id，可选。
     *
     * <p>出库时填上，就构成"工单消耗了哪些配件"的记录；
     * 入库一般不需要填。
     */
    private Long relatedRepairId;

    /** 本次单价。不填则出库取配件当前的参考单价 */
    private BigDecimal unitPrice;

    @Size(max = 200, message = "备注不能超过 200 个字符")
    private String remark;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Long getRelatedRepairId() {
        return relatedRepairId;
    }

    public void setRelatedRepairId(Long relatedRepairId) {
        this.relatedRepairId = relatedRepairId;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
