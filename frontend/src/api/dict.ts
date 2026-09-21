import { onMounted, ref } from 'vue'
import request from '../utils/request'

/** 字典项，对应后端 entity/SysDictItem.java */
export interface SysDictItem {
  id: number
  dictType: string
  /** 展示文案，如「机械故障」。可以随时改 */
  itemLabel: string
  /** 实际存进业务表的值，如 MECH。改了会影响历史数据的匹配 */
  itemValue: string
  sortOrder?: number
  status: string
  remark?: string
}

/** 字典类型，对应后端 entity/SysDictType.java */
export interface SysDictType {
  id: number
  dictName: string
  /** 类型编码，如 fault_type。**创建后不能改** */
  dictType: string
  status: string
  remark?: string
}

/** 下拉选项（后端只返回 value/label，不带备注等管理字段） */
export interface DictOption {
  value: string
  label: string
}

export const DICT_STATUS = {
  NORMAL: '正常',
  DISABLED: '停用',
} as const

export const DICT_STATUS_OPTIONS = [
  { label: '正常', value: DICT_STATUS.NORMAL },
  { label: '停用', value: DICT_STATUS.DISABLED },
]

/** 已知的字典类型编码。和后端 common/DictTypes.java 保持一致 */
export const DICT_TYPE = {
  FAULT_TYPE: 'fault_type',
} as const

// ============================================================
// 业务用的只读接口（登录即可）
// ============================================================

/**
 * 字典选项的内存缓存。
 *
 * <p>字典是"读极多、写极少"的数据：同一个页面可能好几个下拉都用它，
 * 来回切换页面又会重新拉。缓存住 Promise（而不是结果）是为了让
 * **并发调用也只会发一个请求** —— 两个组件同时挂载时，第二个会复用第一个的 Promise。
 */
const optionCache = new Map<string, Promise<DictOption[]>>()

/**
 * 取某个字典启用中的选项。
 *
 * @param force 跳过缓存重新拉取（字典管理页改完之后要用）
 */
export function getDictOptions(dictType: string, force = false): Promise<DictOption[]> {
  if (force) {
    optionCache.delete(dictType)
  }
  let cached = optionCache.get(dictType)
  if (!cached) {
    cached = request.get<DictOption[]>(`/dict/${dictType}`)
    optionCache.set(dictType, cached)
    // 请求失败要把缓存清掉，否则这个失败的 Promise 会一直挂着 ——
    // 之后所有调用都直接拿到同一个失败，连重试的机会都没有
    cached.catch(() => optionCache.delete(dictType))
  }
  return cached
}

/** 清空字典缓存。字典管理页保存之后调，避免其它页面还是旧选项 */
export function clearDictCache() {
  optionCache.clear()
}

/**
 * 在组件里用字典。
 *
 * <p>返回 options（给下拉用）和 labelOf（把存进业务表的值转成展示文案）。
 *
 * <pre>
 *   const faultTypes = useDict(DICT_TYPE.FAULT_TYPE)
 *   &lt;el-option v-for="o in faultTypes.options" ... /&gt;
 *   {{ faultTypes.labelOf(row.faultType) }}
 * </pre>
 */
export function useDict(dictType: string) {
  const options = ref<DictOption[]>([])

  async function load(force = false) {
    try {
      options.value = await getDictOptions(dictType, force)
    } catch {
      // 字典拿不到不该让整个页面挂掉：下拉为空，展示处退回原值
      options.value = []
    }
  }

  onMounted(() => {
    void load()
  })

  /**
   * 把值转成展示文案。
   *
   * <p>查不到时**退回原值**而不是显示空白 —— 字典项被删掉之后，
   * 历史工单里的值仍然能显示出来（虽然是个编码），总比一片空白让人以为没填要好。
   */
  function labelOf(value?: string | null): string {
    if (!value) return '—'
    return options.value.find((o) => o.value === value)?.label ?? value
  }

  return { options, labelOf, reload: () => load(true) }
}

// ============================================================
// 字典管理（管理员）
// ============================================================

export function getDictTypes() {
  return request.get<SysDictType[]>('/system/dict/types')
}

export function createDictType(data: Partial<SysDictType>) {
  return request.post<SysDictType>('/system/dict/types', data)
}

export function updateDictType(id: number, data: Partial<SysDictType>) {
  return request.put<SysDictType>(`/system/dict/types/${id}`, data)
}

export function deleteDictType(id: number) {
  return request.delete<void>(`/system/dict/types/${id}`)
}

/** 字典项列表。onlyEnabled=false 时包含停用的（管理页要能改停用的项） */
export function getDictItems(dictType: string, onlyEnabled = false) {
  return request.get<SysDictItem[]>('/system/dict/items', {
    params: { dictType, onlyEnabled },
  })
}

export function createDictItem(data: Partial<SysDictItem>) {
  return request.post<SysDictItem>('/system/dict/items', data)
}

export function updateDictItem(id: number, data: Partial<SysDictItem>) {
  return request.put<SysDictItem>(`/system/dict/items/${id}`, data)
}

export function deleteDictItem(id: number) {
  return request.delete<void>(`/system/dict/items/${id}`)
}
