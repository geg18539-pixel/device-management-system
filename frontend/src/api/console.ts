import request from '../utils/request'

/**
 * 管理后台首页的系统概览。对应后端 dto/ConsoleOverviewVO.java。
 *
 * ★ 和 api/dashboard.ts 是两件事：那个看的是**业务**（设备、工单、维保），
 *   这个看的是**系统本身**（账号、登录、越权、知识库）。
 *   后端也是两个接口，不要合并。
 */
export interface ConsoleOverview {
  // ---- 需要关注 ----
  /** 今日登录失败次数。突然变多通常是撞库信号 */
  loginFailToday: number
  /** 今日登录成功次数。给失败次数一个参照 */
  loginSuccessToday: number
  /** 今日被拦截的越权访问次数 */
  deniedToday: number
  /** 停用的账号数 */
  disabledUsers: number
  /** 知识库处理失败的文档数 */
  knowledgeFailed: number

  // ---- 规模 ----
  userTotal: number
  roleTotal: number
  deptTotal: number
  knowledgeTotal: number

  // ---- 最近登录 ----
  recentLogins: LoginRecord[]
}

export interface LoginRecord {
  username: string
  ip?: string
  /** 已经归好类的设备描述，如「Windows / Chrome」 */
  device?: string
  success: boolean
  failReason?: string
  loginTime?: string
}

/** 后台首页的全部数字 */
export function getConsoleOverview() {
  return request.get<ConsoleOverview>('/system/overview')
}
