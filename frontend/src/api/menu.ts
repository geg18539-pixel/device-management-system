import request from '../utils/request'
import type { PlateTone } from '../utils/plateTone'

/** 菜单类型：M 目录 / F 菜单 / B 按钮（按钮只做权限控制，不出现在侧边栏） */
export const MENU_TYPE = {
  DIR: 'M',
  MENU: 'F',
  BUTTON: 'B',
} as const

export type MenuType = (typeof MENU_TYPE)[keyof typeof MENU_TYPE]

export const MENU_TYPE_OPTIONS = [
  { label: '目录', value: MENU_TYPE.DIR },
  { label: '菜单', value: MENU_TYPE.MENU },
  { label: '按钮', value: MENU_TYPE.BUTTON },
]

/** 类型铭牌的显示文案与语义色，菜单管理和角色授权树共用 */
export const MENU_TYPE_META: Record<string, { label: string; tone: PlateTone }> = {
  [MENU_TYPE.DIR]: { label: '目录', tone: 'info' },
  [MENU_TYPE.MENU]: { label: '菜单', tone: 'ok' },
  [MENU_TYPE.BUTTON]: { label: '按钮', tone: 'idle' },
}

/** 对应后端 dto/SysMenuTreeVO.java */
export interface SysMenuTree {
  id: number
  parentId: number
  menuName: string
  path?: string
  component?: string
  menuType: MenuType
  perms?: string
  icon?: string
  sortOrder?: number
  visible?: number
  children: SysMenuTree[]
}

/** 新增 / 编辑菜单的提交结构（父级 id 为 0 表示顶级） */
export interface SysMenuForm {
  parentId: number
  menuName: string
  path?: string
  component?: string
  menuType: MenuType
  perms?: string
  icon?: string
  sortOrder?: number
  visible?: number
}

/** 树形结构，给 el-tree 和树形表格用 */
export function getMenuTree() {
  return request.get<SysMenuTree[]>('/system/menus/tree')
}

export function getMenuById(id: number) {
  return request.get<SysMenuTree>(`/system/menus/${id}`)
}

export function createMenu(data: SysMenuForm) {
  return request.post<SysMenuTree>('/system/menus', data)
}

export function updateMenu(id: number, data: SysMenuForm) {
  return request.put<SysMenuTree>(`/system/menus/${id}`, data)
}

export function deleteMenu(id: number) {
  return request.delete<void>(`/system/menus/${id}`)
}
