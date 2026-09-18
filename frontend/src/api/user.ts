import request from '../utils/request'
import type { PageResult } from './types'

/** 对应后端 dto/SysUserVO.java。注意里面**没有** password 字段 */
export interface SysUser {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: string
  createTime?: string
  /** 已分配的角色 id，供"分配角色"弹窗回显 */
  roleIds?: number[]
  /** 角色名称，供表格直接展示 */
  roleNames?: string[]
}

/**
 * 新增 / 编辑用户的提交结构。
 *
 * password 在新增时必填、编辑时留空表示不改密码
 * （后端 SysUserServiceImpl 里按这个规则处理）。
 */
export interface SysUserForm {
  username: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
  status: string
  roleIds: number[]
}

export interface SysUserQuery {
  pageNum: number
  pageSize: number
  username?: string
}

export function getUserPage(params: SysUserQuery) {
  return request.get<PageResult<SysUser>>('/system/users', { params })
}

export function getUserById(id: number) {
  return request.get<SysUser>(`/system/users/${id}`)
}

export function createUser(data: SysUserForm) {
  return request.post<SysUser>('/system/users', data)
}

export function updateUser(id: number, data: SysUserForm) {
  return request.put<SysUser>(`/system/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<void>(`/system/users/${id}`)
}

/** 该用户当前的角色 id，供弹窗回显勾选状态 */
export function getUserRoleIds(id: number) {
  return request.get<number[]>(`/system/users/${id}/roles`)
}

/** 分配角色，传的是角色 id 的全量列表（空数组表示清空） */
export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put<void>(`/system/users/${id}/roles`, { roleIds })
}

export function resetUserPassword(id: number, password: string) {
  return request.put<void>(`/system/users/${id}/password`, { password })
}
