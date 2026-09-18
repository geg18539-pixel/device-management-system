import request from '../utils/request'

/**
 * 设备实体。
 *
 * 字段与后端 entity/Device.java 一一对应。
 * 注意 status 是**字符串**而不是数字 —— 后端用 @NotBlank 校验的 String，
 * 取值是 '在线' / '离线' / '维修中'，不是 1/0。
 */
export interface Device {
  id?: number
  deviceName: string
  deviceType: string
  serialNumber?: string
  status: string
  location?: string
  description?: string
  createTime?: string
  updateTime?: string
}

/** 新增 / 编辑时提交的字段，id 和时间戳由后端维护，不参与提交 */
export type DeviceForm = Omit<Device, 'id' | 'createTime' | 'updateTime'>

/**
 * 设备状态常量。
 *
 * 用 as const 对象而不是 enum —— tsconfig 开了 erasableSyntaxOnly，
 * enum 属于"不可擦除"语法，编译会报错。
 */
export const DEVICE_STATUS = {
  ONLINE: '在线',
  OFFLINE: '离线',
  REPAIRING: '维修中',
} as const

export type DeviceStatus = (typeof DEVICE_STATUS)[keyof typeof DEVICE_STATUS]

/** 状态下拉框选项 */
export const DEVICE_STATUS_OPTIONS = [
  { label: '在线', value: DEVICE_STATUS.ONLINE },
  { label: '离线', value: DEVICE_STATUS.OFFLINE },
  { label: '维修中', value: DEVICE_STATUS.REPAIRING },
]

/** 查询全部设备 —— GET /api/devices */
export function getDeviceList() {
  return request.get<Device[]>('/devices')
}

/** 按 id 查询 —— GET /api/devices/{id} */
export function getDeviceById(id: number) {
  return request.get<Device>(`/devices/${id}`)
}

/** 新增设备 —— POST /api/devices */
export function createDevice(data: DeviceForm) {
  return request.post<Device>('/devices', data)
}

/** 更新设备 —— PUT /api/devices/{id} */
export function updateDevice(id: number, data: DeviceForm) {
  return request.put<Device>(`/devices/${id}`, data)
}

/** 删除设备 —— DELETE /api/devices/{id} */
export function deleteDevice(id: number) {
  return request.delete<void>(`/devices/${id}`)
}
