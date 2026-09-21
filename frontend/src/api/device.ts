import request from '../utils/request'
import { saveBlob, today } from '../utils/download'
import type { PageResult } from './types'
// 详情页要拼装这些模块的数据，这里只引类型。
// 注意方向是单向的：maintenance / sparePart / deviceRepair 都不 import 本模块，
// 所以不会成环
import type { DeviceRepair } from './deviceRepair'
import type { PlateTone } from '../utils/plateTone'
import type { MaintenancePlan, MaintenanceRecord } from './maintenance'
import type { SparePartRecord } from './sparePart'

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

/**
 * 资产生命周期状态。
 *
 * ★ 和上面的 DEVICE_STATUS 是**两个不同维度**，别混用：
 *   status          连通性 —— 设备当前能不能通信（在线/离线/使用中/维修中）
 *   lifecycleStatus 资产状态 —— 这台设备在账上处于生命周期哪个阶段
 * 一台设备可以「在线且已报废」（信号还在，但资产上要淘汰），
 * 也可以「离线但正常」（只是网断了）。
 */
export const DEVICE_LIFECYCLE = {
  NORMAL: '正常',
  REPAIR: '维修',
  SCRAPPED: '报废',
  DISABLED: '停用',
} as const

export type DeviceLifecycle = (typeof DEVICE_LIFECYCLE)[keyof typeof DEVICE_LIFECYCLE]

export const DEVICE_LIFECYCLE_OPTIONS = [
  { label: '正常', value: DEVICE_LIFECYCLE.NORMAL },
  { label: '维修', value: DEVICE_LIFECYCLE.REPAIR },
  { label: '报废', value: DEVICE_LIFECYCLE.SCRAPPED },
  { label: '停用', value: DEVICE_LIFECYCLE.DISABLED },
]

/**
 * 生命周期对应的铭牌色调，列表页和设备档案共用。
 *
 * <p>值是组件层 StatusPlate 的语义色，**不再用 Element Plus 的 el-tag type** ——
 * EP 那套（success/warning/danger/info）和这里"四档状态 + 信息色"的划分对不上，
 * 中间多转一层反而容易出错（原来「停用」映射成 info，看着像"信息"而不是"停用"）。
 */
export const DEVICE_LIFECYCLE_TONE: Record<string, PlateTone> = {
  [DEVICE_LIFECYCLE.NORMAL]: 'ok',
  [DEVICE_LIFECYCLE.REPAIR]: 'warn',
  [DEVICE_LIFECYCLE.SCRAPPED]: 'crit',
  [DEVICE_LIFECYCLE.DISABLED]: 'idle',
}

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
  /** 资产生命周期状态：正常 / 维修 / 报废 / 停用。老数据可能为空，按「正常」处理 */
  lifecycleStatus?: string
  /** 归属部门 id，为空表示未分配 */
  deptId?: number
  /** 型号 */
  model?: string
  /** 生产厂商 */
  manufacturer?: string
  location?: string
  description?: string
  purchaseDate?: string
  warrantyDate?: string
  /** 当前借用人，为空表示未借出 */
  borrower?: string
  borrowTime?: string
  /** 报废日期。为空表示未报废 */
  scrapDate?: string
  scrapReason?: string
  scrapOperator?: string
  createTime?: string
  updateTime?: string
}

export interface DeviceQuery {
  pageNum: number
  pageSize: number
  categoryId?: number
  /** 按归属部门筛选 */
  deptId?: number
  /** 按**连通状态**筛选：在线 / 离线 / 维修中 / 使用中 */
  status?: string
  /** 按**资产状态**筛选：正常 / 维修 / 报废 / 停用 */
  lifecycleStatus?: string
  /** 保修到期在 N 天以内（含已过保） */
  warrantyWithinDays?: number
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

/**
 * 报修，后端生成维修工单并把状态改成"维修中"。
 *
 * <p>返回的是**设备**（因为报修同时改了设备状态），不是工单 ——
 * 要拿工单需要按设备反查列表。
 *
 * @param faultType 故障类型，传**字典项的值**（如 MECH）。选填
 */
export function repairDevice(
  id: number,
  faultDesc: string,
  faultType?: string,
  reporter?: string,
) {
  return request.post<Device>(`/devices/${id}/repair`, { faultDesc, faultType, reporter })
}

// ---------------- 调拨 / 报废 ----------------

/** 一次调拨记录。对应后端 entity/DeviceTransfer */
export interface DeviceTransfer {
  id: number
  deviceId: number
  deviceName?: string
  fromDeptId?: number
  /** 原部门名快照。为空表示调拨前未分配 */
  fromDeptName?: string
  toDeptId?: number
  toDeptName?: string
  reason: string
  operator?: string
  transferTime: string
}

/**
 * 调拨：把设备改到目标部门并留下记录。
 *
 * <p>目标部门传 `undefined` 表示调到"未分配"。
 * **注意设备归属部门只能通过这个接口改**，编辑表单不会改 deptId ——
 * 否则调拨历史会漏掉那些变更。
 */
export function transferDevice(id: number, toDeptId: number | undefined, reason: string) {
  return request.post<Device>(`/devices/${id}/transfer`, { toDeptId, reason })
}

/** 报废：状态改成"报废"并记录日期/原因/操作人 */
export function scrapDevice(id: number, reason: string, scrapDate?: string) {
  return request.post<Device>(`/devices/${id}/scrap`, { reason, scrapDate })
}

/** 取消报废（误操作补救），状态改回"正常" */
export function restoreDevice(id: number) {
  return request.post<Device>(`/devices/${id}/restore`)
}

/** 某台设备的调拨历史 */
export function getDeviceTransfers(id: number) {
  return request.get<DeviceTransfer[]>(`/devices/${id}/transfers`)
}

// ---------------- 设备档案详情 ----------------

/** 设备档案详情：一次拿齐详情页要的全部内容 */
export interface DeviceProfile {
  device: Device
  maintenancePlan?: MaintenancePlan
  maintenanceRecords: MaintenanceRecord[]
  repairHistory: DeviceRepair[]
  /** 配件更换记录：由该设备的工单反查出的出库流水 */
  partRecords: SparePartRecord[]
  attachments: DeviceAttachment[]
  transfers: DeviceTransfer[]
}

/** 附件元数据。对应后端 entity/DeviceAttachment */
export interface DeviceAttachment {
  id: number
  deviceId: number
  /** 用户上传时的原始文件名 */
  fileName: string
  storedName: string
  contentType?: string
  fileSize?: number
  uploader?: string
  uploadTime?: string
}

export function getDeviceProfile(id: number) {
  return request.get<DeviceProfile>(`/devices/${id}/profile`)
}

// ---------------- 设备健康分 ----------------

/**
 * 设备的健康评估结果。对应后端 dto/DeviceHealthVO.java。
 *
 * <p>分值是**实时算出来的**，不是存在库里的字段 —— 每次请求都会跟着
 * 最新的维修记录和资产状态重算，所以不会出现"看板和详情页分数不一样"
 * 或者"工单关掉了分数没变"的情况。
 */
export interface DeviceHealth {
  deviceId: number
  deviceName: string
  assetCode?: string
  /** 未分配部门时后端会给「未分配」而不是空 */
  deptName: string
  lifecycleStatus?: string
  score: number
  /** 良好 / 关注 / 高风险。文案由后端给，前端只决定颜色 */
  grade: string
  /** 扣分原因，人话，按扣分从多到少排。空数组表示没有扣分项 */
  reasons: string[]
}

/**
 * 健康等级对应的色调。
 *
 * <p>只映射颜色不映射文案 —— 等级的中文（良好/关注/高风险）由后端给，
 * 前端再抄一份就会漂移。
 */
export const HEALTH_GRADE_TONE: Record<string, PlateTone> = {
  良好: 'ok',
  关注: 'warn',
  高风险: 'crit',
}

export function healthGradeTone(grade?: string): PlateTone {
  return HEALTH_GRADE_TONE[grade ?? ''] ?? 'idle'
}

/** 单台设备的健康分。设备详情页用 */
export function getDeviceHealth(id: number) {
  return request.get<DeviceHealth>(`/devices/${id}/health`)
}

// ---------------- 附件 ----------------

export function getDeviceAttachments(deviceId: number) {
  return request.get<DeviceAttachment[]>(`/devices/${deviceId}/attachments`)
}

/**
 * 上传附件。
 *
 * <p>必须用 FormData —— 手动设 Content-Type 反而会出错：
 * multipart 的 Content-Type 里带 boundary，浏览器得自己拼。
 * axios 收到 FormData 时会自动设置正确的头，所以这里**不要**手写。
 */
export function uploadDeviceAttachment(deviceId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<DeviceAttachment>(`/devices/${deviceId}/attachments`, form)
}

export function deleteAttachment(id: number) {
  return request.delete<void>(`/device-attachments/${id}`)
}

/**
 * 取附件内容。
 *
 * <p>走 axios 而不是直接把 URL 塞给 `<img src>`：附件接口是需要登录的，
 * `<img>` 发不出 Authorization 头。所以只能先取回二进制再转成 blob URL。
 *
 * @param inline true 表示图片走内联预览（后端会保留 image/* 类型）
 */
export async function fetchAttachmentBlob(id: number, inline: boolean): Promise<Blob> {
  return request.get<Blob>(`/device-attachments/${id}/download`, {
    params: { inline },
    responseType: 'blob',
  })
}

/** 下载附件到本地 */
export async function downloadAttachment(id: number, fileName: string): Promise<void> {
  const blob = await fetchAttachmentBlob(id, false)
  saveBlob(blob, fileName)
}

// ---------------- 台账 ----------------

/** 台账的一行（按部门 / 按分类汇总） */
export interface LedgerItem {
  name: string
  total: number
  normal: number
  repairing: number
  scrapped: number
  disabled: number
}

export interface DeviceLedger {
  total: number
  normal: number
  repairing: number
  scrapped: number
  disabled: number
  deptItems: LedgerItem[]
  categoryItems: LedgerItem[]
}

export function getDeviceLedger() {
  return request.get<DeviceLedger>('/devices/ledger')
}

// ---------------- 导出 ----------------
// 下载怎么触发、文件名里的日期怎么取，都在 utils/download.ts ——
// 这段原来在 device / deviceRepair / user 三个文件里各有一份，现在收成一处。

/** 按当前筛选条件导出设备列表（所见即所得） */
export async function exportDevices(query: Omit<DeviceQuery, 'pageNum' | 'pageSize'>) {
  const blob = await request.get<Blob>('/devices/export', {
    params: query,
    responseType: 'blob',
  })
  saveBlob(blob, `设备列表_${today()}.xlsx`)
}

/** 导出完整台账（一个工作簿三个 sheet：明细 + 按部门 + 按分类） */
export async function exportLedger(query: Omit<DeviceQuery, 'pageNum' | 'pageSize'>) {
  const blob = await request.get<Blob>('/devices/ledger/export', {
    params: query,
    responseType: 'blob',
  })
  saveBlob(blob, `设备台账_${today()}.xlsx`)
}

// ---------------- 批量导入 ----------------

/** 导入失败的一行 */
export interface ImportFailedRow {
  /** Excel 里的行号（从 1 开始、含表头，和用户在 Excel 里看到的一致） */
  rowNum: number
  deviceName?: string
  reason: string
}

/** 对应后端 dto/DeviceImportResultVO.java */
export interface DeviceImportResult {
  /** 文件里的数据行数（不含表头和空行） */
  total: number
  successCount: number
  failCount: number
  errors: ImportFailedRow[]
  /** 失败明细是否被截断（错误太多时后端只返回前 50 条） */
  errorsTruncated: boolean
  /** 非致命的提醒，比如"检测到模板自带的示例行" */
  warnings: string[]
}

/** 下载导入模板（带示例行和填写说明） */
export async function downloadImportTemplate(): Promise<void> {
  const blob = await request.get<Blob>('/devices/import-template', {
    responseType: 'blob',
  })
  saveBlob(blob, '设备导入模板.xlsx')
}

/**
 * 上传并导入。
 *
 * <p>**单独把超时放大到 2 分钟**：批量导入是同步处理的（用户点一下就该看到结果），
 * 2000 行加上解析和入库可能超过默认的 10 秒。
 */
export function importDevices(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<DeviceImportResult>('/devices/import', form, { timeout: 120000 })
}
