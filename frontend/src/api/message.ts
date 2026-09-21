import request from '../utils/request'
import type { PageResult } from './types'
import type { PlateTone } from '../utils/plateTone'

/** 对应后端 entity/SysMessage.java */
export interface SysMessage {
  id: number
  /** 消息类型，见下面的 MSG_TYPE */
  msgType: string
  title: string
  content?: string
  /** 普通 / 重要。重要的前端标红 */
  level: string
  /** 业务类型，决定点击后跳哪，见下面的 BIZ_TYPE */
  bizType?: string
  bizId?: number
  readFlag?: boolean
  readTime?: string
  senderName?: string
  createTime?: string
}

export const MSG_TYPE = {
  SYSTEM: 'SYSTEM',
  REPAIR_ASSIGN: 'REPAIR_ASSIGN',
  MAINTENANCE_DUE: 'MAINTENANCE_DUE',
  STOCK_LOW: 'STOCK_LOW',
  HEALTH_RISK: 'HEALTH_RISK',
} as const

export const MSG_TYPE_OPTIONS = [
  { label: '系统通知', value: MSG_TYPE.SYSTEM },
  { label: '工单指派', value: MSG_TYPE.REPAIR_ASSIGN },
  { label: '维保提醒', value: MSG_TYPE.MAINTENANCE_DUE },
  { label: '库存告急', value: MSG_TYPE.STOCK_LOW },
  { label: '健康预警', value: MSG_TYPE.HEALTH_RISK },
]

/** 消息类型的显示文案与语义色 */
export const MSG_TYPE_META: Record<string, { label: string; tone: PlateTone }> = {
  [MSG_TYPE.SYSTEM]: { label: '系统通知', tone: 'idle' },
  [MSG_TYPE.REPAIR_ASSIGN]: { label: '工单指派', tone: 'info' },
  [MSG_TYPE.MAINTENANCE_DUE]: { label: '维保提醒', tone: 'warn' },
  // 库存告急用 crit 而不是 warn：它是"现在就缺料、今天就得补"，
  // 和维保"还有 30 天到期"不是一个紧急度
  [MSG_TYPE.STOCK_LOW]: { label: '库存告急', tone: 'crit' },
  [MSG_TYPE.HEALTH_RISK]: { label: '健康预警', tone: 'warn' },
}

/**
 * 消息能指向的业务对象。
 *
 * <p>收成常量而不是在 routeForMessage 里写字符串字面量：这几个值要和后端的
 * {@code SysMessage.BIZ_*} 一一对上，散着写的话**拼错了不报错**，
 * 只会表现为"点消息没反应"。和 MSG_TYPE 一个道理。
 */
export const BIZ_TYPE = {
  REPAIR: 'REPAIR',
  MAINTENANCE_PLAN: 'MAINTENANCE_PLAN',
  SPARE_PART: 'SPARE_PART',
  DEVICE: 'DEVICE',
  DASHBOARD: 'DASHBOARD',
} as const

export const MSG_LEVEL = {
  NORMAL: '普通',
  IMPORTANT: '重要',
} as const

/**
 * 点击消息后该跳到哪。
 *
 * <p>返回 null 表示这条消息没有可跳转的业务（比如纯文本的系统通知）。
 *
 * <p>⚠️ **聚合类消息跳看板而不是某一条记录**：健康预警一条消息里列了多台设备，
 * 挂到任何一台的档案页都是误导。看板上有完整清单，而且
 * 那份清单和这条消息是同一个口径（同一个 {@code DeviceHealthService.summary}）。
 */
export function routeForMessage(msg: SysMessage): string | null {
  if (!msg.bizType) return null
  if (msg.bizType === BIZ_TYPE.REPAIR) return '/device-repairs'
  if (msg.bizType === BIZ_TYPE.MAINTENANCE_PLAN) return '/maintenance'
  if (msg.bizType === BIZ_TYPE.SPARE_PART) return '/spare-parts'
  if (msg.bizType === BIZ_TYPE.DASHBOARD) return '/dashboard'
  // 单台设备。没有 id 就退化成设备列表，总比点了没反应好
  if (msg.bizType === BIZ_TYPE.DEVICE) {
    return msg.bizId ? `/devices/${msg.bizId}` : '/devices'
  }
  return null
}

// ---------------- 接口 ----------------

/** 未读数（顶栏红点） */
export function getUnreadCount() {
  return request.get<number>('/messages/unread-count')
}

/** 最近几条（顶栏下拉）。后端把上限卡在 20 */
export function getRecentMessages(limit = 5) {
  return request.get<SysMessage[]>('/messages/recent', { params: { limit } })
}

export function getMessagePage(params: {
  pageNum: number
  pageSize: number
  readFlag?: boolean
  msgType?: string
}) {
  return request.get<PageResult<SysMessage>>('/messages', { params })
}

export function markMessageRead(id: number) {
  return request.put<SysMessage>(`/messages/${id}/read`)
}

export function markAllMessagesRead() {
  return request.put<number>('/messages/read-all')
}

export function deleteMessage(id: number) {
  return request.delete<void>(`/messages/${id}`)
}

/** 清空自己的已读消息，返回删除条数 */
export function deleteAllReadMessages() {
  return request.delete<number>('/messages/read')
}
