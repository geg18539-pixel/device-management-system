package com.yan.backend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户列表的查询条件。
 *
 * <p>用单独的对象而不是一串方法参数：参数已经有 6 个了，再往下加会变成
 * {@code page(1, 10, "zhang", 2L, "正常", begin, end, sortField, sortOrder)}
 * 这种调用方必然写错的签名。列表查询和导出都复用这一个对象。
 */
public class SysUserQuery {

    private String username;

    /** 按角色筛选 */
    private Long roleId;

    /** 按所属部门筛选 */
    private Long deptId;

    /** 按状态筛选：正常 / 停用 */
    private String status;

    /**
     * 创建时间范围。
     *
     * <p>前端传的是日期（YYYY-MM-DD），后端用 LocalDate 接收。
     * 注意结束日期要按 **当天 23:59:59** 处理，否则"创建时间到 09-19"会把
     * 09-19 当天的用户全部漏掉 —— 这是日期范围查询最经典的 off-by-one 错误。
     */
    private LocalDate createTimeBegin;
    private LocalDate createTimeEnd;

    /** 排序字段，只允许 id / createTime（白名单，避免前端传任意字段） */
    private String sortField = "id";

    /** asc / desc */
    private String sortOrder = "desc";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getCreateTimeBegin() {
        return createTimeBegin;
    }

    public void setCreateTimeBegin(LocalDate createTimeBegin) {
        this.createTimeBegin = createTimeBegin;
    }

    public LocalDate getCreateTimeEnd() {
        return createTimeEnd;
    }

    public void setCreateTimeEnd(LocalDate createTimeEnd) {
        this.createTimeEnd = createTimeEnd;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 允许排序的字段白名单 */
    public static final List<String> ALLOWED_SORT_FIELDS = List.of("id", "createTime", "username");
}
