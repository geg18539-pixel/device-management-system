package com.yan.backend.dto;

/**
 * 一条字段级变更明细。
 *
 * <p>{@code field} 直接存**中文标签**（「状态」「所属部门」），不存实体属性名：
 * 这一列只给人看、不做查询条件，用属性名的话前端还得再维护一份
 * 属性名→中文的映射，两边一旦不同步就会出现审计记录里露出
 * 「lifecycleStatus」这种东西。审计资料是给人读的，从一开始就存人话。
 */
public record AuditFieldChange(String field, String before, String after) {
}
