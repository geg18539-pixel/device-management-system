package com.yan.backend.service;

import com.yan.backend.dto.BatchResultVO;
import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserQuery;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;

import java.util.List;

public interface SysUserService {

    /** 分页 + 多条件筛选（用户名 / 角色 / 状态 / 创建时间范围 / 排序） */
    PageResult<SysUserVO> page(SysUserQuery query, int pageNum, int pageSize);

    /** 按同样的筛选条件取出全部数据，给导出用（不分页） */
    List<SysUserVO> listForExport(SysUserQuery query);

    SysUserVO findById(Long id);

    SysUserVO create(SysUserSaveRequest request);

    SysUserVO update(Long id, SysUserSaveRequest request);

    void delete(Long id);

    // ---------------- 批量操作 ----------------
    // 批量操作不做"全成功或全失败"：一部分被保护规则挡下时，
    // 其余的照常执行，并把被跳过的连同原因返回给前端展示。

    BatchResultVO batchDelete(List<Long> ids);

    BatchResultVO batchUpdateStatus(List<Long> ids, String status);

    BatchResultVO batchResetPassword(List<Long> ids, String password);

    // ---------------- 其它 ----------------

    /** 给用户分配角色，传的是角色 id 全量列表 */
    void assignRoles(Long userId, List<Long> roleIds);

    /** 用户当前拥有的角色 id，供弹窗回显 */
    List<Long> findRoleIds(Long userId);

    void resetPassword(Long userId, String newPassword);
}
