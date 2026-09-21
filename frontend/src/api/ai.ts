import request from '../utils/request'
import { useUserStore } from '../stores/user'

export interface AiChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface AiModels {
  models: string[]
  defaultModel: string
}

/** 某一类用途（对话 / 嵌入）当前生效的配置 */
export interface AiProviderStatus {
  /** ollama / openai */
  provider: string
  providerLabel: string
  /** **生效的**服务地址（库里没配就用配置文件/环境变量的） */
  baseUrl: string
  /** **生效的**模型名 */
  model: string
  /** 密钥配没配。**后端只返回布尔值，永远不会回显内容** */
  apiKeyConfigured: boolean
  /** 支不支持函数调用。只有对话侧有意义 */
  supportsTools: boolean
}

export interface AiStatus {
  /** 可选的提供方，给下拉用 */
  providers: { value: string; label: string }[]
  chat: AiProviderStatus
  embedding: AiProviderStatus
}

/** 当前生效的 AI 配置摘要（脱敏，不含密钥） */
export function getAiStatus() {
  return request.get<AiStatus>('/ai/status')
}

/**
 * 列出当前提供方下可用的模型。**同时充当连通性检查**。
 *
 * <p>失败时抛出的异常里已经写清了是哪一家、哪个地址、要不要配密钥，
 * 由调用方的拦截器统一弹出来。
 *
 * @param purpose chat（对话）或 embedding（嵌入）—— 两边可以接不同的提供方，要分别测
 */
export function getAiModels(purpose: 'chat' | 'embedding' = 'chat') {
  return request.get<AiModels>('/ai/models', { params: { purpose } })
}

/**
 * 流式对话。
 *
 * <p><b>这里为什么不用封装好的 request.ts？</b>
 * 因为 axios 的 XHR adapter 会把整个响应体读完才 resolve 请求，
 * 流式响应经过它会退化成"等很久然后一次性拿到全部内容"。
 * 要真正逐块拿到数据，只能用 fetch + ReadableStream。
 *
 * <p>代价是要自己处理一些 request.ts 已经替你做过的事：
 * 手动带 token、手动处理 401、手动拼错误信息。下面都补上了。
 *
 * @param onChunk  每收到一段文本回调一次
 * @param signal   用于中断生成（AbortController）
 */
export async function streamChat(
  messages: AiChatMessage[],
  model: string | undefined,
  onChunk: (text: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore()
  const token = userStore.token

  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ messages, model }),
    signal,
  })

  // 这个请求没经过 axios，所以 request.ts 里那套 401 跳登录的逻辑不会触发，得自己来
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

      // stream: true 是关键：中文字符在 UTF-8 里占 3 个字节，
      // 很容易正好被切在两个网络分块之间。不加这个参数，
      // 被切开的那半个字符会立刻解码成乱码（而正确做法是把它留到下一块一起解）。
      onChunk(decoder.decode(value, { stream: true }))
    }
    // 收尾：把解码器内部可能残留的字节冲出来
    onChunk(decoder.decode())
  } finally {
    reader.releaseLock()
  }
}
