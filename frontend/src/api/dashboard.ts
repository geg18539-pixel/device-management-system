import request from '../utils/request'
import type { ChartItem, Device, DeviceHealth } from './device'
import type { MaintenancePlan } from './maintenance'

/**
 * 首页看板统计数据。对应后端 dto/DashboardStatsVO.java。
 *
 * <p>字段分四组：指标卡数字、图表数据、待办清单、配置项。
 * 一次请求全拿回来，避免首屏分批闪现。
 */
export interface DashboardStats {
  // ---- 指标卡 ----
  deviceTotal: number
  lifecycleNormal: number
  lifecycleRepair: number
  lifecycleScrapped: number
  lifecycleDisabled: number
  borrowedCount: number

  // ---- 工单 ----
  repairTotal: number
  repairPending: number
  repairFinished: number
  repairClosed: number
  /** 完成率，百分比数值（已含 ×100），保留一位小数 */
  repairCompletionRate: number

  // ---- 维保提醒 ----
  /** 厂商保修期快到 / 已过保的设备数 */
  warrantyExpiringCount: number
  /** 提前多少天算「即将到期」，由后端配置 */
  warrantyWarnDays: number

  /**
   * 即将到期 / 已逾期的**维保计划**数量。
   *
   * ★ 和 warrantyExpiringCount 是两回事：那个是"厂商保修期快到了"，
   * 这个是"该安排保养了"。企业设备管理里两个都要盯。
   */
  maintenanceDueCount: number
  maintenanceWarnDays: number

  /** 库存告急的配件数 */
  lowStockCount: number

  // ---- 设备健康（实时算出来的，不落表）----

  /** 健康分偏低（<90）的设备台数。已排除报废设备 */
  healthRiskCount: number
  /**
   * 健康分最低的若干台，按分数升序。
   *
   * <p>条数受后端限制（当前 5 条），所以"共几台"要看 healthRiskCount，
   * 不能用这个数组的长度 —— 后端已经保证两者同源，不会对不上。
   */
  healthRiskDevices: DeviceHealth[]

  // ---- 图表 ----
  lifecycleItems: ChartItem[]
  repairStatusItems: ChartItem[]
  deptDeviceItems: ChartItem[]

  // ---- 清单 ----
  warrantyExpiringDevices: Device[]
  maintenanceDuePlans: MaintenancePlan[]
}

/** 看板的全部统计数据 */
export function getDashboardStats() {
  return request.get<DashboardStats>('/dashboard/stats')
}

/**
 * 首页 AI 摘要。对应后端 dto/DashboardDigestVO.java。
 *
 * ★ available=false 不等于出错 —— 刚部署完、或者应用刚重启还没预热完，
 *   都会是 false，那是正常状态。真正"正在做"的信号是 generating。
 *   所以界面上的分支是三个而不是两个：有正文 / 正在生成 / 其他。
 *
 * ★ message 在 available=true 时**也可能非空** —— 那表示最近一次生成失败了，
 *   但上一次的正文还在，于是界面显示的是"旧正文 + 一行失败提示"。
 */
export interface DashboardDigest {
  /** 摘要正文。available=false 时是空串 */
  text: string
  /** 生成时间，ISO 字符串。null 表示从未生成过 */
  generatedAt: string | null
  /** 生成用的模型名。null 表示还没生成过 */
  model: string | null
  /** 有没有可以展示的正文 */
  available: boolean
  /** 是否正在生成。true 时页面会轮询等结果 */
  generating: boolean
  /** 功能是否开启。false 时整块不渲染 */
  enabled: boolean
  /** 状态提示，可能是空串 */
  message: string
}

/**
 * 取当前那份摘要。
 *
 * <p>**只读缓存，不会触发生成** —— 它跟着首页一起加载，必须立刻返回。
 */
export function getDashboardDigest() {
  return request.get<DashboardDigest>('/dashboard/digest')
}

/**
 * 手动触发一次重新生成。
 *
 * <p>**立刻返回**，不等模型跑完。要判断生成完没有，看返回体里的 generating。
 * 后端有最小间隔保护，太频繁地调用只会拿回同一个结果（generating 为 false）。
 */
export function refreshDashboardDigest() {
  return request.post<DashboardDigest>('/dashboard/digest/refresh')
}
