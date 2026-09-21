import request from '../utils/request'

/** 用户下拉选项。对应后端 controller/UserOptionController */
export interface UserOption {
  /** 用户名。**存进业务字段的就是它** —— 站内通知按用户名解析账号 */
  value: string
  /** 下拉展示用，带用户名，如「王强（wangqiang）」 */
  label: string
  /** 表格展示用，只要昵称，如「王强」 */
  name: string
}

/**
 * 用户选项的内存缓存。
 *
 * <p>和字典一样是"读极多、写极少"，而且好几个页面都要用
 * （工单指派、维保负责人、列表里把用户名显示成人名）。
 * 缓存 Promise 而不是结果，让并发调用只发一个请求。
 */
let cache: Promise<UserOption[]> | null = null

/**
 * 取启用中的用户选项。
 *
 * <p>走的是 {@code /api/users/options} 而不是 {@code /api/system/users} ——
 * 后者在 {@code /api/system/**} 下，只有管理员能调，
 * 普通操作员指派工单时拿不到，下拉会是空的。
 */
export function getUserOptions(force = false): Promise<UserOption[]> {
  if (force) {
    cache = null
  }
  if (!cache) {
    cache = request.get<UserOption[]>('/users/options')
    // 失败要清掉，否则这个失败的 Promise 会一直挂着，之后连重试的机会都没有
    cache.catch(() => (cache = null))
  }
  return cache
}

/** 清缓存。用户被增删改之后调 */
export function clearUserOptionCache() {
  cache = null
}

/**
 * 把存进业务字段的用户名转成展示用的人名。
 *
 * <p>查不到时**退回原值**：历史数据里可能是手工填的姓名（外部维修工），
 * 也可能对应账号已被删除 —— 显示原值总比显示空白好。
 */
export function buildUserNameMap(options: UserOption[]): Record<string, string> {
  const map: Record<string, string> = {}
  for (const opt of options) {
    map[opt.value] = opt.name
  }
  return map
}
