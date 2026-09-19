<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiModels, streamChat, type AiChatMessage } from '../api/ai'

interface ChatItem {
  role: 'user' | 'assistant'
  content: string
  /** 正在生成中的那条，用来显示光标动画 */
  streaming?: boolean
  /** 出错的那条 */
  error?: boolean
}

const messages = ref<ChatItem[]>([])

const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLDivElement>()

// ---------------- Ollama 连通性 ----------------
const models = ref<string[]>([])
const selectedModel = ref('')
const ollamaReady = ref(false)
const checking = ref(true)

async function loadModels() {
  checking.value = true
  try {
    const data = await getAiModels()
    models.value = data.models
    selectedModel.value = data.defaultModel
    ollamaReady.value = true

    // 配置的默认模型在本地不存在时给个提示 —— 否则用户发消息才发现跑不通
    if (data.defaultModel && !data.models.includes(data.defaultModel)) {
      ElMessage.warning(
        `配置的默认模型「${data.defaultModel}」在本地未安装，请选择其它模型或执行 ollama pull ${data.defaultModel}`,
      )
    }
  } catch {
    // 拿不到模型列表通常就意味着 Ollama 没启动，
    // request.ts 的拦截器已经把具体原因弹出来了，这里只切到未连接状态
    ollamaReady.value = false
  } finally {
    checking.value = false
  }
}

// ---------------- 发送 ----------------
let abortController: AbortController | null = null

const canSend = computed(() => !sending.value && input.value.trim().length > 0)

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

async function handleSend() {
  const text = input.value.trim()
  if (!text || sending.value) return

  input.value = ''
  messages.value.push({ role: 'user', content: text })

  // 先把"空的助手消息"推进列表，流式内容往它身上追加，
  // 这样界面上是逐字长出来的，而不是等全部生成完再一次性插入
  const reply: ChatItem = { role: 'assistant', content: '', streaming: true }
  messages.value.push(reply)
  await scrollToBottom()

  sending.value = true
  abortController = new AbortController()

  try {
    // 把历史一起发上去 —— 后端是无状态的，上下文靠前端每次带全
    const history: AiChatMessage[] = messages.value
      .filter((item) => !item.streaming && !item.error && item.content)
      .map((item) => ({ role: item.role, content: item.content }))

    await streamChat(
      history,
      selectedModel.value || undefined,
      (chunk) => {
        reply.content += chunk
        void scrollToBottom()
      },
      abortController.signal,
    )
  } catch (e) {
    // 用户主动中断不算错误
    if (e instanceof DOMException && e.name === 'AbortError') {
      reply.content += '\n\n[已中断生成]'
    } else {
      reply.error = true
      reply.content = reply.content || `请求失败：${e instanceof Error ? e.message : String(e)}`
      ElMessage.error('AI 请求失败')
    }
  } finally {
    reply.streaming = false
    sending.value = false
    abortController = null
    await scrollToBottom()
  }
}

function handleStop() {
  abortController?.abort()
}

function handleClear() {
  if (sending.value) {
    ElMessage.warning('正在生成中，请先中断')
    return
  }
  messages.value = []
}

/** Enter 发送，Shift+Enter 换行 */
function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    void handleSend()
  }
}

// 点击直接填入输入框。
// 分成两类示例是有意为之：前两个走数据快照（不查库），后两个会触发工具调用去查库，
// 用户点一下就能看出两种问法的区别。
const sampleQuestions = [
  '一共有多少台设备？',
  '设备状态分布是怎样的？',
  '现在哪些设备在维修中？',
  '温度传感器 A 保修到什么时候？',
]

onMounted(loadModels)
</script>

<template>
  <div class="ai-page">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="left">
        <span class="label">模型</span>
        <el-select
          v-model="selectedModel"
          :disabled="!ollamaReady || sending"
          placeholder="未连接"
          style="width: 190px"
          size="small"
        >
          <el-option v-for="m in models" :key="m" :label="m" :value="m" />
        </el-select>
        <el-tag v-if="ollamaReady" type="success" size="small" disable-transitions>
          已连接 Ollama
        </el-tag>
        <el-tag v-else-if="!checking" type="danger" size="small" disable-transitions>
          未连接
        </el-tag>
      </div>

      <div class="right">
        <el-button size="small" :disabled="sending" @click="loadModels">重新检测</el-button>
        <el-button size="small" :disabled="sending" @click="handleClear">清空对话</el-button>
      </div>
    </div>

    <!-- 未连接时的提示 -->
    <el-alert
      v-if="!checking && !ollamaReady"
      type="error"
      :closable="false"
      show-icon
      class="alert"
    >
      <template #title>连不上本地 Ollama</template>
      <div class="alert-body">
        <p>请确认 Ollama 已经启动。在命令行执行 <code>ollama list</code> 应当能列出模型。</p>
        <p>
          如果 Ollama 装在别的地址，或后端跑在 Docker 里，需要配置
          <code>OLLAMA_BASE_URL</code>（Docker 场景要指向
          <code>http://host.docker.internal:11434</code>）。
        </p>
      </div>
    </el-alert>

    <!-- 消息区 -->
    <div ref="scrollRef" class="messages">
      <el-empty
        v-if="messages.length === 0"
        description="可以直接问设备数据，比如："
      >
        <div class="samples">
          <el-button
            v-for="q in sampleQuestions"
            :key="q"
            size="small"
            @click="input = q"
          >
            {{ q }}
          </el-button>
        </div>
      </el-empty>

      <div
        v-for="(item, index) in messages"
        :key="index"
        class="row"
        :class="item.role"
      >
        <div class="bubble" :class="{ error: item.error }">
          <span class="text">{{ item.content }}</span>
          <span v-if="item.streaming" class="cursor">▌</span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="composer">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        resize="none"
        placeholder="输入问题，Enter 发送，Shift+Enter 换行"
        :disabled="sending"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <el-button v-if="sending" size="small" @click="handleStop">中断生成</el-button>
        <el-button
          type="primary"
          :disabled="!canSend"
          :loading="sending"
          @click="handleSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  text-align: left;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.left,
.right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  font-size: 13px;
  color: #64748b;
}

.alert {
  margin-bottom: 12px;
}

.alert-body p {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.7;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
}

.row {
  display: flex;
  margin-bottom: 14px;
}

.row.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.row.user .bubble {
  background: #1677ff;
  color: #fff;
}

.row.assistant .bubble {
  background: #f4f6f8;
  color: var(--text-h);
}

.bubble.error {
  background: #fef2f2;
  color: #b91c1c;
}

/* 生成中的光标 */
.cursor {
  display: inline-block;
  margin-left: 2px;
  animation: blink 1s step-start infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.composer {
  margin-top: 12px;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--code-bg);
  font-family: var(--mono);
}

.samples {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.samples :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
