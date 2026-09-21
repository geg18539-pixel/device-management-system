import request from '../utils/request'
import { saveBlob, today } from '../utils/download'
import type { PageResult } from './types'

/**
 * 工单状态。流转：待受理 → 维修中 → 已完成 → 已关闭，
 * 另有 待受理 → 已关闭（误报作废）这条捷径。
 */
export const REPAIR_STATUS = {
  PENDING: '待受理',
  REPAIRING: '维修中',
  FINISHED: '已完成',
  CLOSED: '已关闭',
} as const

export type RepairStatus = (typeof REPAIR_STATUS)[keyof typeof REPAIR_STATUS]

/**
 * ★ 旧版本的「待受理」值。
 *
 * <p>企业级改造之前报修建单写的是「待维修」。库里的历史工单仍然是这个值，
 * 而后端**没有做批量数据迁移**（约定是"新数据写新值，旧值只在判断和展示时等价处理"）。
 */
export const LEGACY_PENDING_STATUS = '待维修'

export const REPAIR_STATUS_OPTIONS = [
  { label: '待受理', value: REPAIR_STATUS.PENDING },
  { label: '维修中', value: REPAIR_STATUS.REPAIRING },
  { label: '已完成', value: REPAIR_STATUS.FINISHED },
  { label: '已关闭', value: REPAIR_STATUS.CLOSED },
]

/** 把旧值归一到当前语义。展示和判断都要用它，别直接比较 */
export function normalizeRepairStatus(status?: string): string {
  if (!status) return ''
  return status === LEGACY_PENDING_STATUS ? REPAIR_STATUS.PENDING : status
}

/** 是否已到终态。待处理 = 不在终态里 */
export function isTerminalStatus(status?: string): boolean {
  const s = normalizeRepairStatus(status)
  return s === REPAIR_STATUS.FINISHED || s === REPAIR_STATUS.CLOSED
}

export function isPendingStatus(status?: string): boolean {
  return normalizeRepairStatus(status) === REPAIR_STATUS.PENDING
}

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
  /**
   * 故障类型。存的是**字典项的值**（如 MECH），不是展示文案。
   *
   * <p>为空兼容两种情况：老工单（加这个字段之前的），以及报修时没选类型。
   * 展示时要用字典把值转成文案，字典里查不到就退回原值。
   */
  faultType?: string
  repairStatus: RepairStatus | string
  reporter?: string
  repairer?: string
  reportTime?: string
  finishTime?: string
  cost?: number
  remark?: string
  createTime?: string

  // ---------- 状态流转记录 ----------
  /** 指派维修人员的时间 */
  assignTime?: string
  /** 受理（开始维修）时间 */
  acceptTime?: string
  /** 关闭时间 */
  closeTime?: string
  /** 关闭原因 */
  closeReason?: string
  /** 维修结果：具体做了什么、换过哪些件 */
  repairResult?: string

  // ---------- AI 智能分析结果 ----------
  aiStatus?: string
  aiSeverity?: string
  aiPossibleCauses?: string
  aiSuggestion?: string
  aiEstimatedHours?: number
  aiModel?: string
  aiAnalyzedAt?: string
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

/** 对应后端 entity/RepairAttachment.java */
export interface RepairAttachment {
  id: number
  repairId: number
  fileName: string
  storedName: string
  contentType?: string
  fileSize?: number
  uploader?: string
  uploadTime?: string
}

/** 列表查询条件，和后端 dto/DeviceRepairQuery.java 一一对应 */
export interface DeviceRepairQuery {
  pageNum: number
  pageSize: number
  repairStatus?: string
  deviceId?: number
  repairer?: string
  /** 按故障类型筛选。传字典项的值（如 MECH），不是展示文案 */
  faultType?: string
  keyword?: string
  /** 只看待处理（不在终态里的工单）。它**跨多个状态**，所以单独一个开关 */
  pendingOnly?: boolean
}

export interface DeviceRepairFinishForm {
  /** 留空则用已指派的人，再没有就用当前登录用户 */
  repairer?: string
  /** 维修结果，必填 */
  repairResult: string
  cost?: number
  remark?: string
}

/** 工单统计（统计看板用） */
export interface RepairStats {
  total: number
  pending: number
  repairing: number
  finished: number
  closed: number
  /** 待处理 = 不在终态里的工单（待受理 + 维修中 + 以后的中间态） */
  pendingCount: number
  completionRate: number
  thisMonthCount: number
  avgRepairHours?: number
  avgSampleSize: number
  statusItems: { name: string; value: number }[]
}

// ---------------- 查询 ----------------

export function getRepairPage(params: DeviceRepairQuery) {
  return request.get<PageResult<DeviceRepair>>('/device-repairs', { params })
}

export function getRepairById(id: number) {
  return request.get<DeviceRepair>(`/device-repairs/${id}`)
}

export function getRepairStats() {
  return request.get<RepairStats>('/device-repairs/stats')
}

// ---------------- 状态流转 ----------------

/** 受理：待受理 → 维修中。会记下受理时间，用于统计"多久有人接单" */
export function acceptRepair(id: number) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/accept`)
}

/** 指派 / 改派维修人员 */
export function assignRepair(id: number, repairer: string) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/assign`, { repairer })
}

/** 完工：维修中 → 已完成。会顺带把设备状态改回"在线" */
export function finishRepair(id: number, data: DeviceRepairFinishForm) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/finish`, data)
}

/**
 * 关闭工单。
 *
 * <p>允许「待受理 → 已关闭」（误报作废）和「已完成 → 已关闭」（归档）。
 * **维修中不能直接关闭**，后端会拒绝，必须先完工交代处理结果。
 */
export function closeRepair(id: number, reason: string) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/close`, { reason })
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

// ---------------- 维修照片 ----------------

export function getRepairAttachments(repairId: number) {
  return request.get<RepairAttachment[]>(`/device-repairs/${repairId}/attachments`)
}

/** 上传照片。必须用 FormData —— Content-Type 里的 boundary 要由浏览器自己拼 */
export function uploadRepairAttachment(repairId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<RepairAttachment>(`/device-repairs/${repairId}/attachments`, form)
}

export function deleteRepairAttachment(id: number) {
  return request.delete<void>(`/repair-attachments/${id}`)
}

/**
 * 取照片内容。
 *
 * <p>走 axios 而不是把 URL 塞给 `<img src>`：附件接口要登录，
 * `<img>` 发不出 Authorization 头。只能先取回二进制再转 blob URL。
 */
export async function fetchRepairAttachmentBlob(id: number, inline: boolean): Promise<Blob> {
  return request.get<Blob>(`/repair-attachments/${id}/download`, {
    params: { inline },
    responseType: 'blob',
  })
}

/** 下载工单附件。下载怎么触发见 utils/download.ts */
export async function downloadRepairAttachment(id: number, fileName: string): Promise<void> {
  const blob = await fetchRepairAttachmentBlob(id, false)
  saveBlob(blob, fileName)
}

// ---------------- 导出 ----------------

/** 按当前筛选条件导出工单 Excel（所见即所得） */
export async function exportRepairs(query: Omit<DeviceRepairQuery, 'pageNum' | 'pageSize'>) {
  const blob = await request.get<Blob>('/device-repairs/export', {
    params: query,
    responseType: 'blob',
  })
  saveBlob(blob, `维修工单_${today()}.xlsx`)
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
