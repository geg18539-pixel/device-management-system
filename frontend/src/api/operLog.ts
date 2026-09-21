import request from '../utils/request'
import type { PageResult } from './types'
import type { PlateTone } from '../utils/plateTone'

/** 对应后端 entity/SysOperLog.java */
export interface OperLog {
  id: number
  /** 操作模块，取自后端 @Log 的 title */
  title?: string
  /** INSERT / UPDATE / DELETE / EXPORT / OTHER */
  businessType?: string
  requestMethod?: string
  /** 请求 URL（含查询串） */
  requestUrl?: string
  /** 被调用的方法签名 */
  method?: string
  operatorId?: number
  operatorName?: string
  ip?: string
  /** 成功 / 失败 */
  status?: string
  /** 失败原因。越权被拒的记录也在这里 */
  errorMsg?: string
  costTime?: number
  operTime?: string
}

export interface OperLogQuery {
  pageNum: number
  pageSize: number
  operatorName?: string
  title?: string
  businessType?: string
  status?: string
  /** 日期范围，格式 YYYY-MM-DD */
  beginDate?: string
  endDate?: string
  keyword?: string
}

export const BUSINESS_TYPE_OPTIONS = [
  { label: '新增', value: 'INSERT' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '导出', value: 'EXPORT' },
  { label: '导入', value: 'IMPORT' },
  { label: '其它', value: 'OTHER' },
]

/** 业务类型的显示文案 */
export function businessTypeLabel(type?: string): string {
  return BUSINESS_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type ?? '—'
}

/** 业务类型对应的铭牌色调 */
export function businessTypeTone(type?: string): PlateTone {
  if (type === 'INSERT') return 'info'
  if (type === 'UPDATE') return 'warn'
  if (type === 'DELETE') return 'crit'
  if (type === 'EXPORT' || type === 'IMPORT') return 'ok'
  return 'idle'
}

/** 操作日志分页查询 —— GET /api/system/oper-logs */
export function getOperLogPage(params: OperLogQuery) {
  return request.get<PageResult<OperLog>>('/system/oper-logs', { params })
}
