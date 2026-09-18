import request from '../utils/request'
import type { PageResult } from './types'

/**
 * 设备状态常量。
 *
 * 用 as const 对象而不是 enum —— tsconfig 开了 erasableSyntaxOnly，
 * enum 属于"不可执行擦除"的语法，编译会直接报错。
 */
export const DEVICE_STATUS = {
  ONLINE: '在线',
  OFFLINE: '离线',
  REPAIRING: '维修中',
  IN_USE: '使用中',
} as const

export type DeviceStatus = (typeof DEVICE_STATUS)[keyof typeof DEVICE_STATUS]

export const DEVICE_STATUS_OPTIONS = [
  { label: '在线', value: DEVICE_STATUS.ONLINE },
  { label: '离线', value: DEVICE_STATUS.OFFLINE },
  { label: '维修中', value: DEVICE_STATUS.REPAIRING },
  { label: '使用中', value: DEVICE_STATUS.IN_USE },
]

/** 对应后端 entity/Device.java */
export interface Device {
  id?: number
  deviceName: string
  /** 遗留字段，5.5 起不再必填，新代码请用 categoryId */
  deviceType?: string
  categoryId?: number
  assetCode?: string
  serialNumber?: string
  status: string
  location?: string
  description?: string
  purchaseDate?: string
  warrantyDate?: string
  /** 当前借用人，为空表示未借出 */
  borrower?: string
  borrowTime?: string
  createTime?: string
  updateTime?: string
}

export interface DeviceQuery {
  pageNum: number
  pageSize: number
  categoryId?: number
  status?: string
  keyword?: string
}

/** 图表用的「名称 - 数值」，字段名对齐 ECharts 的默认约定 */
export interface ChartItem {
  name: string
  value: number
}

export interface DeviceStats {
  statusItems: ChartItem[]
  categoryItems: ChartItem[]
  total: number
}

// ---------------- 查询 ----------------

/** 分页 + 条件筛选，列表页用这个 */
export function getDevicePage(params: DeviceQuery) {
  return request.get<PageResult<Device>>('/devices/page', { params })
}

/** 图表统计数据 */
export function getDeviceStats() {
  return request.get<DeviceStats>('/devices/stats')
}

/** 查全部（不分页）。给需要一次性拿全量的场景用 */
export function getDeviceList() {
  return request.get<Device[]>('/devices')
}

export function getDeviceById(id: number) {
  return request.get<Device>(`/devices/${id}`)
}

// ---------------- 增删改 ----------------

export function createDevice(data: Device) {
  return request.post<Device>('/devices', data)
}

export function updateDevice(id: number, data: Device) {
  return request.put<Device>(`/devices/${id}`, data)
}

export function deleteDevice(id: number) {
  return request.delete<void>(`/devices/${id}`)
}

// ---------------- 借用 / 归还 / 报修 ----------------

/** 借用，后端把状态改成"使用中"并记录借用人 */
export function borrowDevice(id: number, borrower: string, remark?: string) {
  return request.post<Device>(`/devices/${id}/borrow`, { borrower, remark })
}

/** 归还，清空借用信息并把状态改回"在线" */
export function returnDevice(id: number) {
  return request.post<Device>(`/devices/${id}/return`)
}

/** 报修，后端生成维修工单并把状态改成"维修中" */
export function repairDevice(id: number, faultDesc: string, reporter?: string) {
  return request.post<Device>(`/devices/${id}/repair`, { faultDesc, reporter })
}
