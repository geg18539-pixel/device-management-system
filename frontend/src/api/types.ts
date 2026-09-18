/**
 * 跨模块共享的接口类型。
 *
 * 对应后端的 dto/PageResult.java。
 * pageNum 从 1 开始（后端已把 Spring Data 的 0 基页码转换过了）。
 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}
