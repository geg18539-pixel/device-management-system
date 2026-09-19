import request from '../utils/request'
import type { PageResult } from './types'

/**
 * 对应后端 entity/SysRole.java。
 *
 * 后端的 menus 字段加了 @JsonIgnore，所以这里不会有菜单数据 ——
 * 要拿角色的菜单权限走 getRoleMenuIds()。
 */
export interface SysRole {
  id?: number
  roleName: string
  roleKey: string
  sortOrder?: number
  status?: string
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface SysRoleQuery {
  pageNum: number
  pageSize: number
  roleName?: string
}

export function getRolePage(params: SysRoleQuery) {
  return request.get<PageResult<SysRole>>('/system/roles', { params })
}

/** 不分页的全部角色，给"分配角色"弹窗渲染勾选项 */
export function getAllRoles() {
  return request.get<SysRole[]>('/system/roles/all')
}

export function getRoleById(id: number) {
  return request.get<SysRole>(`/system/roles/${id}`)
}

export function createRole(data: SysRole) {
  return request.post<SysRole>('/system/roles', data)
}

export function updateRole(id: number, data: SysRole) {
  return request.put<SysRole>(`/system/roles/${id}`, data)
}

export function deleteRole(id: number) {
  return request.delete<void>(`/system/roles/${id}`)
}

/**
 * 复制角色（连同它已分配的菜单和按钮权限）。
 *
 * 新建出来的角色 roleKey 会加 _copy 后缀，状态默认是**停用** ——
 * 权限还没经人工确认，先不让它生效。
 */
export function copyRole(id: number) {
  return request.post<SysRole>(`/system/roles/${id}/copy`)
}

/** 该角色已拥有的菜单 id，供 el-tree 回显 */
export function getRoleMenuIds(id: number) {
  return request.get<number[]>(`/system/roles/${id}/menus`)
}

/**
 * 分配菜单权限。
 *
 * 传的是菜单 id 的全量列表。注意调用方应当把**全选 + 半选**的节点都传进来
 * （见 RoleView.vue 里的说明），否则父级目录会丢。
 */
export function assignRoleMenus(id: number, menuIds: number[]) {
  return request.put<void>(`/system/roles/${id}/menus`, { menuIds })
}
