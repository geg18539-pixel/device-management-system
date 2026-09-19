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

/** 列出 Ollama 已安装的模型，顺带验证它是否在运行 */
export function getAiModels() {
  return request.get<AiModels>('/ai/models')
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
