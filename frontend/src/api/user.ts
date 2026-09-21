import request from '../utils/request'
import { saveBlob, today } from '../utils/download'
import type { PageResult } from './types'

/** 对应后端 dto/SysUserVO.java。注意里面**没有** password 字段 */
export interface SysUser {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  status: string
  /** 所属部门 id，为空表示未分配 */
  deptId?: number
  createTime?: string
  /** 最后一次登录时间 / 来源 IP，从未登录过则为空 */
  lastLoginTime?: string
  lastLoginIp?: string
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
  /** 所属部门 id。整个字段一起提交，清空即解绑部门 */
  deptId?: number
  roleIds: number[]
}

/** 列表查询条件，和后端 dto/SysUserQuery.java 一一对应 */
export interface SysUserQuery {
  pageNum: number
  pageSize: number
  username?: string
  roleId?: number
  /** 按所属部门筛选 */
  deptId?: number
  status?: string
  /** 创建时间范围，格式 YYYY-MM-DD */
  createTimeBegin?: string
  createTimeEnd?: string
  /** 排序字段，后端有白名单：id / createTime / username */
  sortField?: string
  /** asc / desc */
  sortOrder?: string
}

/** 批量操作结果：成功几条 + 被跳过的那些及原因 */
export interface BatchResult {
  successCount: number
  skipped: { id: number; username: string; reason: string }[]
}

export const USER_STATUS = {
  NORMAL: '正常',
  DISABLED: '停用',
} as const

/**
 * 密码强度校验。
 *
 * <p>规则和后端 common/PasswordPolicy.java 保持一致。
 * 前端校验只是为了**即时反馈**，后端仍然会再校验一次 ——
 * 前端校验永远不能当作安全边界（绕过浏览器直接调接口就失效了）。
 */
export function checkPasswordStrength(password: string): string | null {
  if (!password) return '密码不能为空'
  if (password.length < 8 || password.length > 64) {
    return '密码长度需在 8 到 64 个字符之间'
  }
  const categories = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^a-zA-Z0-9]/].filter((r) =>
    r.test(password),
  ).length
  if (categories < 3) {
    return '密码需至少包含「小写字母、大写字母、数字、特殊符号」中的 3 类'
  }
  return null
}

export const PASSWORD_RULE_TEXT =
  '长度 8-64 位，且至少包含小写字母、大写字母、数字、特殊符号中的 3 类'

// ---------------- 查询 ----------------

export function getUserPage(params: SysUserQuery) {
  return request.get<PageResult<SysUser>>('/system/users', { params })
}

export function getUserById(id: number) {
  return request.get<SysUser>(`/system/users/${id}`)
}

/** 该用户当前的角色 id，供弹窗回显勾选状态 */
export function getUserRoleIds(id: number) {
  return request.get<number[]>(`/system/users/${id}/roles`)
}

// ---------------- 增删改 ----------------

export function createUser(data: SysUserForm) {
  return request.post<SysUser>('/system/users', data)
}

export function updateUser(id: number, data: SysUserForm) {
  return request.put<SysUser>(`/system/users/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<void>(`/system/users/${id}`)
}

/** 分配角色，传的是角色 id 的全量列表（空数组表示清空） */
export function assignUserRoles(id: number, roleIds: number[]) {
  return request.put<void>(`/system/users/${id}/roles`, { roleIds })
}

export function resetUserPassword(id: number, password: string) {
  return request.put<void>(`/system/users/${id}/password`, { password })
}

// ---------------- 批量操作 ----------------

export function batchDeleteUsers(ids: number[]) {
  return request.post<BatchResult>('/system/users/batch/delete', { ids })
}

export function batchUpdateUserStatus(ids: number[], status: string) {
  return request.post<BatchResult>('/system/users/batch/status', { ids, status })
}

export function batchResetUserPassword(ids: number[], password: string) {
  return request.post<BatchResult>('/system/users/batch/password', { ids, password })
}

// ---------------- 导出 ----------------

/**
 * 导出 Excel（筛选条件和列表一致，所见即所得）。
 *
 * <p>用法上要注意：这里返回的是二进制文件，所以必须传 `responseType: 'blob'`，
 * 否则 axios 会按文本解析，得到的是一堆乱码。
 *
 * <p>request.ts 的响应拦截器对这种响应是安全的：它判断"返回体里有没有 code 字段"，
 * Blob 没有 code，于是原样返回给调用方，不会被当成业务数据解包。
 */
export async function exportUsers(query: SysUserQuery): Promise<void> {
  const blob = await request.get<Blob>('/system/users/export', {
    params: query,
    responseType: 'blob',
  })
  saveBlob(blob, `用户列表_${today()}.xlsx`)
}
