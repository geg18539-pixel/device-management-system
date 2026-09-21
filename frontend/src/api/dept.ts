import request from '../utils/request'

/**
 * 部门（树形）。
 *
 * <p>后端有「部门名称 + 上级」的联合唯一约束：
 * 不同上级下允许同名（比如两个分公司下都有"运维部"），同级重名会被拒。
 */
export interface DeptTree {
  id: number
  parentId: number
  deptName: string
  sortOrder?: number
  leader?: string
  phone?: string
  status: string
  remark?: string
  children: DeptTree[]
}

/** 新增 / 编辑部门的提交结构。parentId 为 0 表示顶级部门 */
export interface DeptForm {
  parentId: number
  deptName: string
  sortOrder?: number
  leader?: string
  phone?: string
  status: string
  remark?: string
}

export const DEPT_STATUS = {
  NORMAL: '正常',
  DISABLED: '停用',
} as const

export const DEPT_STATUS_OPTIONS = [
  { label: '正常', value: DEPT_STATUS.NORMAL },
  { label: '停用', value: DEPT_STATUS.DISABLED },
]

/** 顶级部门的 parentId 约定值，和后端 SysDept.ROOT_PARENT_ID 一致 */
export const DEPT_ROOT_PARENT_ID = 0

/** 树形结构，给部门树表格和下拉用 */
export function getDeptTree() {
  return request.get<DeptTree[]>('/system/depts/tree')
}

export function createDept(data: DeptForm) {
  return request.post<DeptTree>('/system/depts', data)
}

export function updateDept(id: number, data: DeptForm) {
  return request.put<DeptTree>(`/system/depts/${id}`, data)
}

export function deleteDept(id: number) {
  return request.delete<void>(`/system/depts/${id}`)
}

export interface DeptOption {
  id: number
  label: string
  /** 层级，从 0 开始。给需要按层级做缩进的场景用 */
  depth: number
}

/**
 * 把部门树压平成下拉选项。
 *
 * <p>和设备分类的 flattenCategories 是同一套路 —— 用缩进表现层级，
 * 比引 el-tree-select 少一个组件的版本兼容风险，而且部门层级一般不会太深。
 *
 * @param rootLabel 顶级那一项的文案（null 表示不插入顶级项）
 */
export function flattenDepts(tree: DeptTree[], rootLabel: string | null = '顶级部门'): DeptOption[] {
  const out: DeptOption[] = []

  if (rootLabel !== null) {
    out.push({ id: DEPT_ROOT_PARENT_ID, label: rootLabel, depth: 0 })
  }

  const walk = (nodes: DeptTree[], depth: number) => {
    for (const node of nodes) {
      out.push({
        id: node.id,
        label: '　'.repeat(rootLabel === null ? depth : depth + 1) + node.deptName,
        depth,
      })
      if (node.children?.length) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(tree, 0)
  return out
}

/** id -> 部门名，用来把表格里的 deptId 显示成部门名 */
export function buildDeptNameMap(tree: DeptTree[]): Record<number, string> {
  const map: Record<number, string> = {}

  const walk = (nodes: DeptTree[]) => {
    for (const node of nodes) {
      map[node.id] = node.deptName
      if (node.children?.length) {
        walk(node.children)
      }
    }
  }
  walk(tree)
  return map
}
