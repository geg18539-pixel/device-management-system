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

/** 完工，后端会顺带把设备状态从"维修中"改回"在线" */
export function finishRepair(id: number, data: DeviceRepairFinishForm) {
  return request.put<DeviceRepair>(`/device-repairs/${id}/finish`, data)
}

export function deleteRepair(id: number) {
  return request.delete<void>(`/device-repairs/${id}`)
}
