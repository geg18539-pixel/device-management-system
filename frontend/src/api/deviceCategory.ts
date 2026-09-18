import request from '../utils/request'

/** 对应后端 dto/DeviceCategoryTreeVO.java */
export interface DeviceCategoryTree {
  id: number
  parentId: number
  categoryName: string
  sortOrder?: number
  remark?: string
  children: DeviceCategoryTree[]
}

export interface DeviceCategoryForm {
  parentId: number
  categoryName: string
  sortOrder?: number
  remark?: string
}

/** 树形结构，给分类下拉和树形表格用 */
export function getCategoryTree() {
  return request.get<DeviceCategoryTree[]>('/device-categories/tree')
}

export function createCategory(data: DeviceCategoryForm) {
  return request.post<DeviceCategoryTree>('/device-categories', data)
}

export function updateCategory(id: number, data: DeviceCategoryForm) {
  return request.put<DeviceCategoryTree>(`/device-categories/${id}`, data)
}

export function deleteCategory(id: number) {
  return request.delete<void>(`/device-categories/${id}`)
}

/**
 * 把分类树压平成下拉选项，用全角空格缩进表示层级。
 *
 * 本来可以用 el-tree-select，但那个组件的 API 在 Element Plus 各版本间
 * 有过调整，而这里层级一般不深，用「平铺 + 缩进」的方式零风险且够用。
 */
export function flattenCategories(
  nodes: DeviceCategoryTree[],
  depth = 0,
  out: { id: number; label: string }[] = [],
): { id: number; label: string }[] {
  for (const node of nodes) {
    out.push({ id: node.id, label: '　'.repeat(depth) + node.categoryName })
    if (node.children?.length) {
      flattenCategories(node.children, depth + 1, out)
    }
  }
  return out
}

/** id -> 名称 的映射，用来在表格里把 categoryId 显示成分类名 */
export function buildCategoryNameMap(
  nodes: DeviceCategoryTree[],
  map: Record<number, string> = {},
): Record<number, string> {
  for (const node of nodes) {
    map[node.id] = node.categoryName
    if (node.children?.length) {
      buildCategoryNameMap(node.children, map)
    }
  }
  return map
}
