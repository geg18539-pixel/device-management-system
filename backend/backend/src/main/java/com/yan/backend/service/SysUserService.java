package com.yan.backend.service;

import com.yan.backend.dto.PageResult;
import com.yan.backend.dto.SysUserSaveRequest;
import com.yan.backend.dto.SysUserVO;

import java.util.List;

public interface SysUserService {

    PageResult<SysUserVO> page(int pageNum, int pageSize, String username);

    SysUserVO findById(Long id);

    SysUserVO create(SysUserSaveRequest request);

    SysUserVO update(Long id, SysUserSaveRequest request);

    void delete(Long id);

    /** 给用户分配角色，传的是角色 id 全量列表 */
    void assignRoles(Long userId, List<Long> roleIds);

    /** 用户当前拥有的角色 id，供弹窗回显 */
    List<Long> findRoleIds(Long userId);

    void resetPassword(Long userId, String newPassword);
}
