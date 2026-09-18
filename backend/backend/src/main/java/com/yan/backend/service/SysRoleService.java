package com.yan.backend.service;

import com.yan.backend.dto.PageResult;
import com.yan.backend.entity.SysRole;

import java.util.List;

public interface SysRoleService {

    PageResult<SysRole> page(int pageNum, int pageSize, String roleName);

    /** 全部角色（不分页），供"分配角色"弹窗渲染勾选项 */
    List<SysRole> listAll();

    SysRole findById(Long id);

    SysRole create(SysRole role);

    SysRole update(Long id, SysRole role);

    void delete(Long id);

    /** 给角色分配菜单权限，传的是菜单 id 全量列表 */
    void assignMenus(Long roleId, List<Long> menuIds);

    /** 角色当前拥有的菜单 id，供弹窗回显 */
    List<Long> findMenuIds(Long roleId);
}
