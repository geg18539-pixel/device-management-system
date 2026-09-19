import request from '../utils/request'
import type { PageResult } from './types'

/** 工单状态 */
export const REPAIR_STATUS = {
  PENDING: '待维修',
  REPAIRING: '维修中',
  FINISHED: '已完成',
} as const

export type RepairStatus = (typeof REPAIR_STATUS)[keyof typeof REPAIR_STATUS]

export const REPAIR_STATUS_OPTIONS = [
  { label: '待维修', value: REPAIR_STATUS.PENDING },
  { label: '维修中', value: REPAIR_STATUS.REPAIRING },
  { label: '已完成', value: REPAIR_STATUS.FINISHED },
]

/** AI 分析状态 */
export const AI_STATUS = {
  PENDING: '待分析',
  RUNNING: '分析中',
  DONE: '已完成',
  FAILED: '失败',
} as const

/** 严重程度 */
export const SEVERITY = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
} as const

/** 维修日志类型 */
export const LOG_TYPE = {
  CREATED: '建单',
  STATUS: '状态变更',
  NOTE: '维修记录',
} as const

/** 对应后端 entity/DeviceRepair.java */
export interface DeviceRepair {
  id: number
  deviceId: number
  /** 报修时设备名的快照，设备后来改名也不影响历史工单 */
  deviceName?: string
  faultDesc: string
  repairStatus: RepairStatus
  reporter?: string
  repairer?: string
  reportTime?: string
  finishTime?: string
  cost?: number
  remark?: string
  createTime?: string

  // ---------- AI 智能分析结果 ----------
  /** 待分析 / 分析中 / 已完成 / 失败。旧工单可能为 null */
  aiStatus?: string
  /** 高 / 中 / 低 */
  aiSeverity?: string
  /** 可能原因，多条用 \n 分隔 */
  aiPossibleCauses?: string
  /** 建议维修步骤，多条用 \n 分隔 */
  aiSuggestion?: string
  aiEstimatedHours?: number
  aiModel?: string
  aiAnalyzedAt?: string
  /** 分析失败的原因 */
  aiError?: string
}

/** 对应后端 entity/DeviceRepairLog.java */
export interface DeviceRepairLog {
  id: number
  repairId: number
  /** 建单 / 状态变更 / 维修记录 */
  logType: string
  content: string
  operator?: string
  logTime?: string
}

export interface DeviceRepairQuery {
  pageNum: number
  pageSize: number
  repairStatus?: string
  deviceId?: number
}

export interface DeviceRepairFinishForm {
  repairer: string
  cost?: number
  remark?: string
}

export function getRepairPage(params: DeviceRepairQuery) {
  return request.get<PageResult<DeviceRepair>>('/device-repairs', { params })
}

/** 按 id 查单张工单。抽屉里轮询 AI 分析结果时用这个 */
export function getRepairById(id: number) {
  return request.get<DeviceRepair>(`/device-repairs/${id}`)
}

/** 完工，后端会顺带把设备状态从"维修中"改回"在线"，并写一条状态变更日志 */
export function finishRepair(id: number, data: DeviceRepairFinishForm) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/finish`, data)
}

export function deleteRepair(id: number) {
  return request.delete<void>(`/device-repairs/${id}`)
}

// ---------------- 维修日志 ----------------

export function getRepairLogs(id: number) {
  return request.get<DeviceRepairLog[]>(`/device-repairs/${id}/logs`)
}

/** 追加维修记录。操作人由后端从登录态取，前端不传 */
export function addRepairLog(id: number, content: string) {
  return request.post<DeviceRepairLog>(`/device-repairs/${id}/logs`, { content })
}

// ---------------- AI 分析 ----------------

/**
 * 重新触发 AI 分析。
 *
 * 接口立即返回，分析在后台线程跑。调用后需要过一会儿再刷新工单看结果
 * （aiStatus 会从「分析中」变成「已完成」或「失败」）。
 */
export function reanalyzeRepair(id: number) {
  return request.post<void>(`/device-repairs/${id}/reanalyze`)
}

/** 把后端用换行拼接的多条文本拆成数组，供页面逐条渲染 */
export function splitLines(value?: string): string[] {
  if (!value) return []
  return value.split('\n').map((s) => s.trim()).filter(Boolean)
}
