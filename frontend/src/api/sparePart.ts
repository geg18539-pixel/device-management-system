import request from '../utils/request'
import type { PageResult } from './types'

/** 配件 / 耗材。对应后端 entity/SparePart */
export interface SparePart {
  id: number
  partCode: string
  partName: string
  model?: string
  unit?: string
  /** 当前库存。**只能通过出入库变动**，编辑表单改不了 */
  stockQuantity: number
  /** 库存预警阈值。库存 ≤ 它就算告急 */
  warnThreshold: number
  unitPrice?: number
  supplier?: string
  location?: string
  status: string
  remark?: string
  createTime?: string
  updateTime?: string
}

/** 出入库流水。对应后端 entity/SparePartRecord */
export interface SparePartRecord {
  id: number
  partId: number
  partCode?: string
  partName?: string
  recordType: string
  quantity: number
  beforeStock: number
  afterStock: number
  relatedRepairId?: number
  unitPrice?: number
  operator?: string
  recordTime: string
  remark?: string
}

/** 新增 / 编辑配件的提交结构（不含库存） */
export interface SparePartForm {
  partCode: string
  partName: string
  model?: string
  unit?: string
  warnThreshold?: number
  unitPrice?: number
  supplier?: string
  location?: string
  status: string
  remark?: string
}

/** 出入库提交结构 */
export interface StockForm {
  quantity: number
  relatedRepairId?: number
  unitPrice?: number
  remark?: string
}

export const PART_STATUS = {
  ENABLED: '启用',
  DISABLED: '停用',
} as const

export const PART_STATUS_OPTIONS = [
  { label: '启用', value: PART_STATUS.ENABLED },
  { label: '停用', value: PART_STATUS.DISABLED },
]

export const PART_RECORD_TYPE = {
  IN: '入库',
  OUT: '出库',
} as const

/** 该配件是不是库存告急 */
export function isLowStock(part: SparePart): boolean {
  return part.stockQuantity <= part.warnThreshold
}

// ---------------- 配件主数据 ----------------

export function getSparePartPage(params: {
  keyword?: string
  status?: string
  lowStockOnly?: boolean
  pageNum: number
  pageSize: number
}) {
  return request.get<PageResult<SparePart>>('/spare-parts', { params })
}

/** 启用中的配件，给出入库下拉用 */
export function getSparePartOptions() {
  return request.get<SparePart[]>('/spare-parts/options')
}

/** 库存告急清单 */
export function getLowStockParts() {
  return request.get<SparePart[]>('/spare-parts/low-stock')
}

export function getSparePartById(id: number) {
  return request.get<SparePart>(`/spare-parts/${id}`)
}

/** 新增：库存会从 0 开始，之后必须走入库 */
export function createSparePart(data: SparePartForm) {
  return request.post<SparePart>('/spare-parts', data)
}

/** 编辑：不接受库存数字（后端会忽略），库存只能走出入库 */
export function updateSparePart(id: number, data: SparePartForm) {
  return request.put<SparePart>(`/spare-parts/${id}`, data)
}

export function deleteSparePart(id: number) {
  return request.delete<void>(`/spare-parts/${id}`)
}

// ---------------- 出入库 ----------------

export function stockIn(partId: number, data: StockForm) {
  return request.post<SparePartRecord>(`/spare-parts/${partId}/stock-in`, data)
}

/** 出库。库存不足后端会返回 400，可以在请求里带 relatedRepairId 关联工单 */
export function stockOut(partId: number, data: StockForm) {
  return request.post<SparePartRecord>(`/spare-parts/${partId}/stock-out`, data)
}

export function getSparePartRecordPage(params: {
  partId?: number
  pageNum: number
  pageSize: number
}) {
  return request.get<PageResult<SparePartRecord>>('/spare-parts/records', { params })
}

/** 某张维修工单消耗的配件 */
export function getRecordsByRepair(repairId: number) {
  return request.get<SparePartRecord[]>(`/spare-parts/records/by-repair/${repairId}`)
}
