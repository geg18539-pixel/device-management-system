import request from '../utils/request'
import type { PageResult } from './types'

/** 维保计划。对应后端 entity/DeviceMaintenancePlan */
export interface MaintenancePlan {
  id: number
  deviceId: number
  deviceName?: string
  planName: string
  /** 保养周期（天） */
  cycleDays: number
  lastMaintenanceDate?: string
  /** 下次到期日。告警只看这个字段 */
  nextMaintenanceDate?: string
  maintainer?: string
  status: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 维保记录。对应后端 entity/DeviceMaintenanceRecord */
export interface MaintenanceRecord {
  id: number
  planId?: number
  deviceId: number
  deviceName?: string
  maintenanceDate: string
  maintainer?: string
  content: string
  result?: string
  cost?: number
  remark?: string
  createTime?: string
}

/** 新增 / 编辑计划的提交结构 */
export interface MaintenancePlanForm {
  deviceId?: number
  planName: string
  cycleDays: number
  lastMaintenanceDate?: string
  nextMaintenanceDate?: string
  maintainer?: string
  status: string
  remark?: string
}

/** 执行一次维保的提交结构 */
export interface MaintenanceExecuteForm {
  maintenanceDate?: string
  maintainer?: string
  content: string
  result?: string
  cost?: number
  remark?: string
}

export const MAINTENANCE_STATUS = {
  ENABLED: '启用',
  DISABLED: '停用',
} as const

export const MAINTENANCE_STATUS_OPTIONS = [
  { label: '启用', value: MAINTENANCE_STATUS.ENABLED },
  { label: '停用', value: MAINTENANCE_STATUS.DISABLED },
]

export const MAINTENANCE_RESULT = {
  NORMAL: '正常',
  NEED_REPAIR: '发现问题待维修',
  REPAIRED: '当场修复',
} as const

export const MAINTENANCE_RESULT_OPTIONS = [
  { label: '正常', value: MAINTENANCE_RESULT.NORMAL },
  { label: '发现问题待维修', value: MAINTENANCE_RESULT.NEED_REPAIR },
  { label: '当场修复', value: MAINTENANCE_RESULT.REPAIRED },
]

/**
 * 判断一个计划是不是已经逾期。
 *
 * <p>只按日期比较（都取 YYYY-MM-DD），不掺时间 ——
 * 掺了时分秒的话"今天到期"会被算成逾期。
 */
export function isOverdue(nextDate?: string): boolean {
  if (!nextDate) return false
  return nextDate < new Date().toISOString().slice(0, 10)
}

/** 距离到期还有几天。负数表示已逾期 */
export function daysUntilDue(nextDate?: string): number | null {
  if (!nextDate) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(`${nextDate}T00:00:00`)
  if (Number.isNaN(target.getTime())) return null
  return Math.round((target.getTime() - today.getTime()) / 86400000)
}

// ---------------- 计划 ----------------

export function getMaintenancePlanPage(params: {
  status?: string
  keyword?: string
  pageNum: number
  pageSize: number
}) {
  return request.get<PageResult<MaintenancePlan>>('/maintenance/plans', { params })
}

export function getPlanById(id: number) {
  return request.get<MaintenancePlan>(`/maintenance/plans/${id}`)
}

/** 即将到期 / 已逾期的计划 */
export function getDuePlans() {
  return request.get<MaintenancePlan[]>('/maintenance/plans/due')
}

/** 某台设备的计划（详情页用） */
export function getPlansByDevice(deviceId: number) {
  return request.get<MaintenancePlan[]>(`/maintenance/devices/${deviceId}/plans`)
}

/** 计划自身没有 id 时（新增）走创建；后端会拒绝对同一设备建第二个计划 */
export function createPlan(data: MaintenancePlanForm) {
  return request.post<MaintenancePlan>('/maintenance/plans', data)
}

export function updatePlan(id: number, data: MaintenancePlanForm) {
  return request.put<MaintenancePlan>(`/maintenance/plans/${id}`, data)
}

export function deletePlan(id: number) {
  return request.delete<void>(`/maintenance/plans/${id}`)
}

// ---------------- 执行维保 ----------------

/** 按计划执行：写记录 + 把下次到期日往后推一个周期 */
export function executeByPlan(planId: number, data: MaintenanceExecuteForm) {
  return request.post<MaintenanceRecord>(`/maintenance/plans/${planId}/execute`, data)
}

/** 临时保养：只写记录，不动任何计划 */
export function executeByDevice(deviceId: number, data: MaintenanceExecuteForm) {
  return request.post<MaintenanceRecord>(`/maintenance/devices/${deviceId}/records`, data)
}

// ---------------- 记录 ----------------

export function getMaintenanceRecordPage(params: {
  deviceId?: number
  pageNum: number
  pageSize: number
}) {
  return request.get<PageResult<MaintenanceRecord>>('/maintenance/records', { params })
}
