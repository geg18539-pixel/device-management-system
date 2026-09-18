package com.yan.backend.dto;

import java.util.List;

/**
 * 给角色分配菜单权限的请求体。
 *
 * <p>与 AssignRolesRequest 同理，传的是菜单 id 的全量列表。
 * 前端 el-tree 勾选后提交的是完整集合（含半选的父节点，由前端决定要不要带上）。
 */
public class AssignMenusRequest {

    private List<Long> menuIds;

    public List<Long> getMenuIds() {
        return menuIds;
    }

    public void setMenuIds(List<Long> menuIds) {
        this.menuIds = menuIds;
    }
}
