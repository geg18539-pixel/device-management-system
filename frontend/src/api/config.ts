import request from '../utils/request'

/** 对应后端 entity/SysConfig.java */
export interface SysConfig {
  id: number
  /** 参数键，如 security.password.valid-days */
  configKey: string
  configValue?: string
  configName: string
  /** 分组，页面按它分块展示 */
  configGroup?: string
  /** STRING / NUMBER / BOOLEAN —— 决定用什么输入控件 */
  valueType: string
  /** 内置参数不允许删除，只能改值 */
  builtIn: boolean
  remark?: string
  sortOrder?: number
  updateTime?: string
}

export const VALUE_TYPE = {
  STRING: 'STRING',
  NUMBER: 'NUMBER',
  BOOLEAN: 'BOOLEAN',
} as const

/** 全部参数（管理页用，需要管理员权限） */
export function getConfigs() {
  return request.get<SysConfig[]>('/system/configs')
}

/**
 * 改某一项的值。
 *
 * <p>按 key 定位而不是 id —— key 稳定且可读，前端拿着配置项直接拼就行。
 * 保存后**立即生效**，不需要重启（后端有缓存，但写操作会清掉它）。
 */
export function updateConfig(configKey: string, configValue: string) {
  return request.put<SysConfig>(`/system/configs/${configKey}`, { configValue })
}

export function createConfig(data: Partial<SysConfig>) {
  return request.post<SysConfig>('/system/configs', data)
}

export function deleteConfig(id: number) {
  return request.delete<void>(`/system/configs/${id}`)
}

/** 免登录可读的展示类参数（登录页用，不要带 token） */
export function getPublicConfig() {
  return request.get<Record<string, string>>('/config/public')
}
