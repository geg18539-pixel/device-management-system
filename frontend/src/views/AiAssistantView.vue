<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiModels, getAiStatus, streamChat, type AiChatMessage } from '../api/ai'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'

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

// ---------------- 服务连通性 ----------------
const models = ref<string[]>([])
const selectedModel = ref('')
/** 连上了没有。名字不叫 aiReady —— 现在也可能是云端的 OpenAI 兼容服务 */
const aiReady = ref(false)
const checking = ref(true)
/** 当前用的是哪一家。显示出来，否则用户不知道是谁在回答 */
const providerLabel = ref('')

/** 拉当前生效的提供方名字。失败不影响使用，只是标题里少一行字 */
async function loadProvider() {
  try {
    const status = await getAiStatus()
    providerLabel.value = status.chat.providerLabel
  } catch {
    providerLabel.value = ''
  }
}

async function loadModels() {
  checking.value = true
  try {
    const data = await getAiModels()
    models.value = data.models
    selectedModel.value = data.defaultModel
    aiReady.value = true

    // 配置的默认模型在对方的模型列表里不存在时给个提示 ——
    // 否则用户要发一条消息才发现跑不通
    if (data.defaultModel && !data.models.includes(data.defaultModel)) {
      ElMessage.warning(
        `配置的默认模型「${data.defaultModel}」不在可用列表里，请换一个，`
        + `或到「系统设置 → AI 模型」核对模型名`,
      )
    }
  } catch {
    // 拿不到模型列表通常意味着服务连不上（或密钥不对），
    // request.ts 的拦截器已经把具体原因弹出来了，这里只切到未连接状态
    aiReady.value = false
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

onMounted(() => {
  void loadModels()
  void loadProvider()
})
</script>

<template>
  <div class="ai-page">
    <PageHeader
      title="AI 助手"
      :desc="`当前提供方：${providerLabel || '读取中…'}；可以问设备数据，对话历史只存在这个浏览器里`"
    />

    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="left">
        <span class="label">模型</span>
        <el-select
          v-model="selectedModel"
          :disabled="!aiReady || sending"
          placeholder="未连接"
          style="width: 190px"
          size="small"
        >
          <el-option v-for="m in models" :key="m" :label="m" :value="m" />
        </el-select>
        <StatusPlate v-if="aiReady" tone="ok">已连接 {{ providerLabel || '模型服务' }}</StatusPlate>
        <StatusPlate v-else-if="!checking" tone="crit">未连接</StatusPlate>
      </div>

      <div class="right">
        <el-button size="small" :disabled="sending" @click="loadModels">重新检测</el-button>
        <el-button size="small" :disabled="sending" @click="handleClear">清空对话</el-button>
      </div>
    </div>

    <!-- 未连接时的提示 -->
    <el-alert
      v-if="!checking && !aiReady"
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
      <EmptyState
        v-if="messages.length === 0"
        title="还没有对话"
        desc="可以直接问设备数据。点下面任意一个问题试试，或者自己输入。"
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
      </EmptyState>

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
/* 聊天页要占满可视高度：顶栏 56 + 内容区上下内边距 40 + 页头约 45。
   这个数字和上面几项是配套的，改外壳高度时要一起调。
   更稳的做法是让 .content 变成 flex 容器、这里用 flex:1，
   但 .content 是所有页面共用的，为一个页面改它有回归风险，先维持现状 */
.ai-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 145px);
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
  color: var(--ink-2);
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
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  background: var(--surface);
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
  border-radius: var(--r-panel);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.row.user .bubble {
  background: var(--signal);
  color: var(--on-signal);
}

.row.assistant .bubble {
  background: var(--canvas);
  color: var(--ink-1);
}

.bubble.error {
  background: var(--crit-weak);
  color: var(--crit);
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
  border-radius: var(--r-control);
  background: var(--sunken);
  font-family: var(--font-mono);
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
