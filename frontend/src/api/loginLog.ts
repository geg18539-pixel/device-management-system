import request from '../utils/request'
import type { PageResult } from './types'

/** 对应后端 entity/SysLoginLog.java */
export interface SysLoginLog {
  id: number
  /** 尝试登录的用户名。登录失败时这个账号可能根本不存在 */
  username: string
  /** 登录成功时才有值 */
  userId?: number
  ip?: string
  /** 原始 User-Agent，鼠标悬停时展示 */
  userAgent?: string
  /** 从 User-Agent 粗略提取的设备描述，如「Windows / Chrome」 */
  device?: string
  success: boolean
  /** 失败原因，成功时为空 */
  failReason?: string
  loginTime: string
}

export interface LoginLogQuery {
  pageNum: number
  pageSize: number
  username?: string
  ip?: string
  /** true 只看成功、false 只看失败、不传表示全部 */
  success?: boolean
  /** 登录时间范围，格式 YYYY-MM-DD */
  loginTimeBegin?: string
  loginTimeEnd?: string
}

/**
 * 分页查询登录日志。
 *
 * 后端固定按登录时间倒序返回（看这个页面的人基本都在追查最近发生了什么），
 * 所以前端不提供排序选项。
 */
export function getLoginLogPage(params: LoginLogQuery) {
  return request.get<PageResult<SysLoginLog>>('/system/login-logs', { params })
}
