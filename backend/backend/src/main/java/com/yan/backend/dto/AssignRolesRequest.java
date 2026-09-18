package com.yan.backend.dto;

import java.util.List;

/**
 * 给用户分配角色的请求体。
 *
 * <p>传的是角色 id 的**全量列表**（不是增量），即"分配后该用户拥有这些角色"。
 * 前端勾选框提交的就是完整选中集合，这样语义最清晰，也不用处理增删差异。
 * 传空数组表示清空该用户的所有角色。
 */
public class AssignRolesRequest {

    private List<Long> roleIds;

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
