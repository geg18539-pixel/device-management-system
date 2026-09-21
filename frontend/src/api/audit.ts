import request from '../utils/request'
import { saveBlob, today } from '../utils/download'
import type { PageResult } from './types'
import type { PlateTone } from '../utils/plateTone'

/** 业务类型。和设备/配件的实体名对应 */
export const AUDIT_BIZ_TYPE = {
  DEVICE: 'DEVICE',
  PART: 'PART',
} as const

/** 一条字段级变更 */
export interface AuditFieldChange {
  /** 字段的中文标签，如「状态」。**后端给的就是中文**，前端不用再映射 */
  field: string
  before: string
  after: string
}

/**
 * 一条审计记录。
 *
 * <p>没有本地定义 action 的枚举值 —— 动作代码和中文都由后端给
 * （{@code action} / {@code actionLabel}）。前端只维护"用什么颜色"，
 * 那是纯展示的事；文案两边各存一份必然会漂移。
 */
export interface AuditLog {
  id: number
  bizType: string
  /** 业务类型中文，如「设备」 */
  bizTypeLabel: string
  bizId: number
  /** 变更时对象名称的快照 */
  bizName?: string
  /** 变更时对象编号的快照 */
  bizCode?: string
  action: string
  /** 变更动作中文，如「调拨」 */
  actionLabel: string
  changes: AuditFieldChange[]
  changeCount: number
  operator?: string
  operatorName?: string
  remark?: string
  auditTime?: string
}

export interface AuditLogQuery {
  bizType?: string
  bizId?: number
  action?: string
  operator?: string
  keyword?: string
  auditTimeBegin?: string
  auditTimeEnd?: string
}

/**
 * 变更动作对应的色调。
 *
 * <p>只影响"用什么颜色"，不定义文案 —— 文案从后端的动作字典拿。
 * 语义划分：**新增/恢复/入库这类"东西变好了"用 ok，
 * 删除/报废用 crit，其余变更用 info 或 warn**。
 */
export const AUDIT_ACTION_TONE: Record<string, PlateTone> = {
  CREATE: 'ok',
  UPDATE: 'info',
  DELETE: 'crit',
  BORROW: 'info',
  RETURN: 'ok',
  REPAIR: 'warn',
  TRANSFER: 'info',
  SCRAP: 'crit',
  RESTORE: 'ok',
  STOCK_IN: 'ok',
  STOCK_OUT: 'warn',
}

/** 未收录的动作回退到中性色，而不是显示成空 */
export function auditToneOf(action: string): PlateTone {
  return AUDIT_ACTION_TONE[action] ?? 'idle'
}

/** 变更动作字典（代码 → 中文），由后端提供 */
export function getAuditActions() {
  return request.get<Record<string, string>>('/system/audit-logs/actions')
}

/** 分页查询审计记录 */
export function pageAuditLogs(
  query: AuditLogQuery,
  pageNum: number,
  pageSize: number,
): Promise<PageResult<AuditLog>> {
  return request.get<PageResult<AuditLog>>('/system/audit-logs', {
    params: { ...query, pageNum, pageSize },
  })
}

/**
 * 某台设备的**全部**变更历史，不分页。
 *
 * <p>设备详情页的「变更审计」页签用它。走分页的话，一台改过 200 次的设备
 * 页签里只会显示第一页，看着像"历史丢了"。
 */
export function listDeviceAuditLogs(deviceId: number): Promise<AuditLog[]> {
  return request.get<AuditLog[]>(`/system/audit-logs/by-device/${deviceId}`)
}

/** 按当前筛选条件导出审计日志（所见即所得） */
export async function exportAuditLogs(query: AuditLogQuery): Promise<void> {
  const blob = await request.get<Blob>('/system/audit-logs/export', {
    params: query,
    responseType: 'blob',
  })
  saveBlob(blob, `资产审计日志_${today()}.xlsx`)
}
