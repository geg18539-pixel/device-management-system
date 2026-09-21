import request from '../utils/request'
import type { PlateTone } from '../utils/plateTone'

/** 文档处理状态。和 DeviceKnowledge 实体里的常量一致 */
export const KNOWLEDGE_STATUS = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  DONE: '已完成',
  FAILED: '失败',
} as const

export interface KnowledgeDoc {
  id: number
  title: string
  /** 上传时的原始文件名 */
  fileName?: string
  fileType?: string
  fileSize?: number
  status: string
  /** 失败原因。成功时为 null */
  errorMsg?: string
  chunkCount: number
  /** 用哪个嵌入模型算的向量 */
  embedModel?: string
  dimension?: number
  uploader?: string
  uploadTime?: string
}

/**
 * 状态对应的色调。
 *
 * <p>只映射颜色不映射文案 —— 状态的中文由实体常量给，两边各存一份会漂移。
 */
export const KNOWLEDGE_STATUS_TONE: Record<string, PlateTone> = {
  [KNOWLEDGE_STATUS.PENDING]: 'idle',
  [KNOWLEDGE_STATUS.PROCESSING]: 'info',
  [KNOWLEDGE_STATUS.DONE]: 'ok',
  [KNOWLEDGE_STATUS.FAILED]: 'crit',
}

export function knowledgeTone(status: string): PlateTone {
  return KNOWLEDGE_STATUS_TONE[status] ?? 'idle'
}

/** 一条命中的知识片段 */
export interface KnowledgeHit {
  chunkId: number
  knowledgeId: number
  docTitle: string
  /** 在文档里的序号，从 0 开始 */
  chunkIndex: number
  content: string
  /** 余弦相似度，越大越相关 */
  score: number
}

export interface KnowledgeSearchResult {
  query: string
  embedModel: string
  dimension: number
  hits: KnowledgeHit[]
  /** 因为嵌入模型/维度不一致被跳过的块数 */
  skippedMismatch: number
  totalIndexed: number
  /**
   * 最相近的一条得了几分，**不管有没有过门槛**。
   *
   * <p>用来区分"门槛调高了"和"确实没有相关内容" —— 前者该调配置，
   * 后者该补资料，动作完全相反。后端的 notice 里已经带了它，
   * 这里列出来是为了让类型和接口一致。
   */
  bestScore: number
  /** 后端给的提示。正常情况下为 null */
  notice?: string
}

/** 全部文档，最新的在前 */
export function listKnowledge(keyword?: string) {
  return request.get<KnowledgeDoc[]>('/knowledge', {
    params: { keyword: keyword || undefined },
  })
}

/**
 * 上传文档。
 *
 * <p>⚠️ **超时单独放大到 5 分钟**：后端是同步做解析、切分、
 * 逐批调 Ollama 嵌入的，一份几百页的 PDF 在 CPU 上可能要几十秒到几分钟。
 * 默认的 10 秒必然超时，而且超时之后文档其实还在处理 ——
 * 用户会以为失败了去重传。
 */
export function uploadKnowledge(file: File, title?: string) {
  const form = new FormData()
  form.append('file', file)
  if (title) {
    form.append('title', title)
  }
  // 不要手写 Content-Type：让浏览器自己带上 multipart 的 boundary，
  // 手动设置反而会让后端解析不出文件
  return request.post<KnowledgeDoc>('/knowledge', form, { timeout: 300000 })
}

/** 重新解析并嵌入。超时同样放大 */
export function reprocessKnowledge(id: number) {
  return request.post<KnowledgeDoc>(`/knowledge/${id}/reprocess`, null, { timeout: 300000 })
}

export function deleteKnowledge(id: number) {
  return request.delete<void>(`/knowledge/${id}`)
}

/** 某个文档切出来的文本块（纯文本，不含向量），用于核对切分效果 */
export function listKnowledgeChunks(id: number) {
  return request.get<string[]>(`/knowledge/${id}/chunks`)
}

/** 语义检索 */
export function searchKnowledge(query: string, topK?: number) {
  return request.post<KnowledgeSearchResult>(
    '/knowledge/search',
    { query, topK },
    { timeout: 120000 },
  )
}
