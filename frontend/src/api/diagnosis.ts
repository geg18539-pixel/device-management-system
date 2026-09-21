import request from '../utils/request'
import { useUserStore } from '../stores/user'

/** 知识库里命中的一段（带来源标题） */
export interface DiagnosisKnowledgeHit {
  chunkId: number
  knowledgeId: number
  docTitle: string
  chunkIndex: number
  content: string
  score: number
}

/** 一条相似历史工单 */
export interface DiagnosisSimilarCase {
  repairId: number
  deviceId: number
  deviceName: string
  faultDesc: string
  faultType?: string
  /** 当时的维修结果。案例最有价值的部分 */
  repairResult?: string
  repairer?: string
  finishTime?: string
  /**
   * 匹配度 0~1。
   *
   * <p>⚠️ 是**词面重合 + 元数据加权**算出来的，**不是语义相似度** ——
   * 界面上要如实写成「匹配度」。
   */
  similarity: number
  /** 为什么算相似：「同一台设备」「同类故障」「描述高度重合」 */
  matchReasons: string[]
}

/** 建议备件：相似工单里实际领用过的 */
export interface DiagnosisSuggestedPart {
  partId: number
  partCode?: string
  partName: string
  unit?: string
  stockQuantity: number
  usageCount: number
}

/** 一次诊断的检索结果 */
export interface DiagnosisContext {
  faultDesc: string
  deviceId?: number
  deviceName?: string
  deviceModel?: string
  knowledgeHits: DiagnosisKnowledgeHit[]
  similarCases: DiagnosisSimilarCase[]
  suggestedParts: DiagnosisSuggestedPart[]
  /** 资料不足时的提示。正常情况下为 null */
  notice?: string
}

export interface DiagnosisRequest {
  faultDesc: string
  deviceId?: number
  faultType?: string
  model?: string
}

/**
 * 只检索，不生成。
 *
 * <p>走普通 axios 就行 —— 它只做一次嵌入 + 查库，是秒级的快接口。
 */
export function retrieveDiagnosis(payload: DiagnosisRequest) {
  return request.post<DiagnosisContext>('/diagnosis/retrieve', payload)
}

/**
 * 检索 + 流式生成。
 *
 * <p>和 `streamChat` 一样必须用 fetch + ReadableStream：
 * axios 的 XHR adapter 会把整个响应读完才 resolve，流式经它会退化成
 * "等几十秒然后一次性出现"。代价是要自己带 token、自己处理 401。
 */
export async function streamDiagnosis(
  payload: DiagnosisRequest,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore()
  const token = userStore.token

  const response = await fetch('/api/diagnosis/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })

  // 这个请求没经过 axios，request.ts 里那套 401 跳登录不会触发，得自己来
  if (response.status === 401) {
    userStore.logout()
    window.location.href = '/login'
    throw new Error('登录已过期，请重新登录')
  }
  if (!response.ok) {
    throw new Error(`请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      // stream: true 不能省：中文在 UTF-8 里占 3 字节，很容易被切在两个网络分块之间
      onChunk(decoder.decode(value, { stream: true }))
    }
    onChunk(decoder.decode())
  } finally {
    reader.releaseLock()
  }
}
