import request from '../utils/request'

/**
 * 自然语言问数。对应后端 dto/QueryResultVO.java 与 dto/QueryCatalogVO.java。
 *
 * ★ 行和列都是**字符串** —— 后端已经按数值排好序、也做过金额格式化，
 *   前端不需要为每个指标各写一套格式化逻辑。
 */

export interface QueryResult {
  /** 用户原话 */
  question: string
  /**
   * 模型复述的"我理解你在问什么"。
   *
   * <p>**必须显示给用户**：小模型一定会有理解错的时候，
   * 让他一眼看出系统理解成了什么，比对着一个莫名其妙的结果猜要好得多。
   */
  understanding: string
  /** 用到的指标名 */
  metricLabel: string
  /** 分组维度名，没分组时为 null */
  groupByLabel?: string | null
  /** 清单形态（表格）还是聚合形态（标签 + 数字） */
  listMode: boolean
  columns: string[]
  rows: string[][]
  rowCount: number
  /** 是否被行数上限截断 */
  truncated: boolean
  /** 后端拼的一句人话结论 */
  summary: string
}

export interface QueryCatalog {
  metrics: { label: string; hint: string; listMode: boolean }[]
  examples: string[]
}

/** 问一句 */
export function askData(question: string) {
  return request.post<QueryResult>('/query/ask', { question })
}

/** 能问什么（指标说明 + 示例问题） */
export function getQueryCatalog() {
  return request.get<QueryCatalog>('/query/catalog')
}
