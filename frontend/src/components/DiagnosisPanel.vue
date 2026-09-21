<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  retrieveDiagnosis,
  streamDiagnosis,
  type DiagnosisContext,
} from '../api/diagnosis'
import { getDevicePage, type Device } from '../api/device'
import StatusPlate from './StatusPlate.vue'
import type { PlateTone } from '../utils/plateTone'

/**
 * 故障诊断面板。
 *
 * <p>独立页面和报修弹窗共用这一个组件：两者的差别只是
 * "要不要选设备"和"故障描述是不是预填的"，逻辑完全一样，
 * 各写一份必然会出现一边改了另一边没改。
 */
const props = withDefaults(
  defineProps<{
    /** 预填的故障描述（从报修弹窗进来时带当前填的内容） */
    initialFaultDesc?: string
    /** 固定设备。传了就不再显示设备选择器 */
    deviceId?: number
    deviceName?: string
    /** 故障类型（字典值）。用于给"同类故障"的历史工单加分 */
    faultType?: string
    /** 是否显示设备选择器。独立页面为 true */
    showDevicePicker?: boolean
  }>(),
  {
    initialFaultDesc: '',
    deviceId: undefined,
    deviceName: '',
    faultType: '',
    showDevicePicker: false,
  },
)

const faultDesc = ref(props.initialFaultDesc)
const pickedDeviceId = ref<number | undefined>(props.deviceId)
const pickedDeviceName = ref(props.deviceName)

const running = ref(false)
const output = ref('')
const context = ref<DiagnosisContext | null>(null)
const outputBox = ref<HTMLElement | null>(null)

/** 停掉正在跑的生成 */
let controller: AbortController | null = null

const effectiveDeviceId = computed(() => props.deviceId ?? pickedDeviceId.value)

// ---- 设备远程搜索 ----
const deviceOptions = ref<Device[]>([])
const deviceLoading = ref(false)

async function searchDevices(keyword: string) {
  deviceLoading.value = true
  try {
    const page = await getDevicePage({ keyword: keyword || undefined, pageNum: 1, pageSize: 20 })
    deviceOptions.value = page.list
  } catch {
    deviceOptions.value = []
  } finally {
    deviceLoading.value = false
  }
}

function onDeviceChange(id: number | undefined) {
  pickedDeviceName.value = deviceOptions.value.find((d) => d.id === id)?.deviceName ?? ''
}

// ============================================================

async function run() {
  const desc = faultDesc.value.trim()
  if (!desc) {
    ElMessage.warning('请先描述故障现象')
    return
  }

  running.value = true
  output.value = ''
  context.value = null

  const payload = {
    faultDesc: desc,
    deviceId: effectiveDeviceId.value,
    faultType: props.faultType || undefined,
  }

  // 1) 先拿检索结果。这是个秒级的快接口，拿到就能立刻显示"依据的是哪些材料"，
  //    不用等模型几十秒
  try {
    context.value = await retrieveDiagnosis(payload)
  } catch {
    // 检索失败不阻断生成 —— 后端那边也做了同样的降级
    context.value = null
  }

  // 2) 再流式拿模型生成的建议
  controller = new AbortController()
  try {
    await streamDiagnosis(
      payload,
      (text) => {
        output.value += text
        // 边收边贴底。这里直接操作 DOM 而不走响应式，是因为
        // 每个分片都触发一次渲染再等 nextTick 会让滚动明显滞后
        requestAnimationFrame(() => {
          if (outputBox.value) {
            outputBox.value.scrollTop = outputBox.value.scrollHeight
          }
        })
      },
      controller.signal,
    )
  } catch (ex) {
    // 用户主动中断不是错误
    if (!(ex instanceof DOMException && ex.name === 'AbortError')) {
      ElMessage.error('诊断失败，请确认 Ollama 已启动')
    }
  } finally {
    running.value = false
    controller = null
  }
}

function abort() {
  controller?.abort()
  running.value = false
}

/**
 * 匹配度的显示分档。
 *
 * <p>⚠️ 这只是给人看的颜色，**不是语义相似度**。
 * 后端算的是词面重合 + 元数据加权，界面上也按「匹配度」措辞。
 */
function matchTone(similarity: number): PlateTone {
  if (similarity >= 0.7) return 'ok'
  if (similarity >= 0.45) return 'warn'
  return 'idle'
}

function stockTone(quantity: number): PlateTone {
  return quantity > 0 ? 'ok' : 'crit'
}

// 外部换了预填内容（比如报修弹窗改了描述再点进来）时同步过来
watch(
  () => props.initialFaultDesc,
  (value) => {
    if (!running.value) {
      faultDesc.value = value
    }
  },
)

onBeforeUnmount(() => {
  // 不中断的话，请求还挂在那里，回调会往一个已经卸载的组件里写数据
  controller?.abort()
})

defineExpose({ run })
</script>

<template>
  <div class="diag">
    <!-- ---------- 输入 ---------- -->
    <div class="input-area">
      <el-select
        v-if="showDevicePicker"
        v-model="pickedDeviceId"
        filterable
        remote
        clearable
        reserve-keyword
        :remote-method="searchDevices"
        :loading="deviceLoading"
        placeholder="指定设备（可不选）"
        class="device-picker"
        @change="onDeviceChange"
      >
        <el-option
          v-for="d in deviceOptions"
          :key="d.id"
          :label="d.deviceName || `设备#${d.id}`"
          :value="d.id"
        >
          <span>{{ d.deviceName }}</span>
          <span class="opt-meta">{{ d.assetCode || '' }}</span>
        </el-option>
      </el-select>

      <span v-else-if="pickedDeviceName" class="fixed-device">
        设备：<b>{{ pickedDeviceName }}</b>
      </span>

      <el-input
        v-model="faultDesc"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="描述故障现象，比如：主轴异响，转速上不去"
        @keydown.enter.exact.prevent="run"
      />

      <div class="actions">
        <el-button type="primary" :loading="running" @click="run">
          {{ running ? '诊断中…' : '开始诊断' }}
        </el-button>
        <el-button v-if="running" @click="abort">停止</el-button>
      </div>
    </div>

    <!-- ---------- 资料不足的提示 ---------- -->
    <p v-if="context?.notice" class="notice">{{ context.notice }}</p>

    <!-- ---------- 检索结果 ---------- -->
    <div v-if="context" class="results">
      <section class="result-col">
        <h4 class="col-title">
          参考手册片段
          <span class="col-count">{{ context.knowledgeHits.length }}</span>
        </h4>
        <div v-if="context.knowledgeHits.length" class="col-body">
          <article v-for="hit in context.knowledgeHits" :key="hit.chunkId" class="hit">
            <header class="hit-head">
              <span class="hit-doc">{{ hit.docTitle }}</span>
              <span class="hit-pos">第 {{ hit.chunkIndex + 1 }} 段</span>
              <span class="hit-score num">{{ hit.score.toFixed(2) }}</span>
            </header>
            <pre class="hit-text">{{ hit.content }}</pre>
          </article>
        </div>
        <p v-else class="col-empty">知识库里没有检索到相关内容</p>
      </section>

      <section class="result-col">
        <h4 class="col-title">
          相似历史工单
          <span class="col-count">{{ context.similarCases.length }}</span>
        </h4>
        <div v-if="context.similarCases.length" class="col-body">
          <article v-for="c in context.similarCases" :key="c.repairId" class="case">
            <header class="case-head">
              <span class="case-device">{{ c.deviceName }}</span>
              <!-- 每条都标出"为什么算相似"。不标的话用户没法判断这条值不值得参考 -->
              <StatusPlate
                v-for="reason in c.matchReasons"
                :key="reason"
                :tone="matchTone(c.similarity)"
                :dot="false"
              >
                {{ reason }}
              </StatusPlate>
              <span class="case-score num">{{ c.similarity.toFixed(2) }}</span>
            </header>
            <p class="case-line"><b>现象：</b>{{ c.faultDesc }}</p>
            <p v-if="c.repairResult" class="case-line"><b>处理：</b>{{ c.repairResult }}</p>
            <p v-else class="case-line muted">（当时没写维修结果）</p>
          </article>
        </div>
        <p v-else class="col-empty">没有找到相似的历史工单</p>
      </section>

      <section class="result-col">
        <h4 class="col-title">
          建议备件
          <span class="col-count">{{ context.suggestedParts.length }}</span>
        </h4>
        <div v-if="context.suggestedParts.length" class="col-body">
          <!-- 说清来源：这些不是模型猜的，是相似工单真的领过的件 -->
          <p class="col-hint">来自相似工单的实际领用记录</p>
          <article v-for="part in context.suggestedParts" :key="part.partId" class="part">
            <div class="part-main">
              <span class="part-name">{{ part.partName }}</span>
              <span class="part-code mono">{{ part.partCode }}</span>
            </div>
            <div class="part-meta">
              <!-- 库存为 0 必须显眼：建议换一个库里没有的件是没用的 -->
              <StatusPlate :tone="stockTone(part.stockQuantity)">
                库存 {{ part.stockQuantity }}
              </StatusPlate>
              <span class="part-usage">用过 {{ part.usageCount }} 次</span>
            </div>
          </article>
        </div>
        <p v-else class="col-empty">相似工单里没有配件领用记录</p>
      </section>
    </div>

    <!-- ---------- AI 建议 ---------- -->
    <div v-if="output || running" class="output-panel">
      <header class="output-head">
        <span class="output-title">AI 维修建议</span>
        <span v-if="running" class="output-hint">生成中…（3B 模型在 CPU 上可能要几十秒）</span>
      </header>
      <pre ref="outputBox" class="output-text">{{ output }}</pre>
    </div>
  </div>
</template>

<style scoped>
.diag {
  text-align: left;
}

/* ---------- 输入 ---------- */
/* 自带面板外观：独立页面和弹窗里都能直接用，不需要各自再包一层 */
.input-area {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

.device-picker {
  width: 220px;
  flex: none;
}

.opt-meta {
  margin-left: 8px;
  font-size: 11px;
  color: var(--ink-3);
}

.fixed-device {
  flex: none;
  font-size: 12px;
  color: var(--ink-3);
  padding-top: 6px;
}

/* 输入框吃掉剩余宽度，按钮固定在右边 */
.input-area :deep(.el-textarea) {
  flex: 1;
  min-width: 260px;
}

.actions {
  flex: none;
  display: flex;
  gap: var(--sp-2);
  padding-top: 1px;
}

/* ---------- 提示 ---------- */
.notice {
  margin-top: var(--sp-3);
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--warn);
  background: var(--warn-weak);
  border-radius: var(--r-control);
}

/* ---------- 检索结果 ---------- */
.results {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.result-col {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

.col-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-1);
}

.col-count {
  padding: 0 6px;
  font-size: 11px;
  font-weight: 400;
  /* ⚠️ 这里必须用 --ink-2 而不是 --ink-3：底色是 --sunken，
     而 --ink-3 是按 --surface / --canvas / --hover 三个面定的值，
     压在 --sunken 上只有 4.23:1，不达 4.5:1。
     tokens.css 的注释里写明了这条约束，这里是它的一个实例。 */
  color: var(--ink-2);
  background: var(--sunken);
  border-radius: var(--r-plate);
}

.col-hint {
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--ink-3);
}

.col-body {
  margin-top: var(--sp-2);
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.col-empty {
  margin-top: var(--sp-2);
  font-size: 12px;
  color: var(--ink-3);
}

/* ---- 手册片段 ---- */
.hit {
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--line-soft);
}

.hit:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.hit-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 11px;
}

.hit-doc {
  color: var(--ink-1);
  font-weight: 500;
}

.hit-pos {
  color: var(--ink-3);
}

.hit-score {
  margin-left: auto;
  color: var(--ink-3);
}

.hit-text {
  margin-top: 4px;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
  /* 片段可能很长，限高让三栏高度接近，不会一栏把页面撑得老长 */
  max-height: 150px;
  overflow-y: auto;
}

/* ---- 相似工单 ---- */
.case {
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--line-soft);
}

.case:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.case-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.case-device {
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-1);
}

.case-score {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
}

.case-line {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-2);
  word-break: break-word;
}

.case-line b {
  font-weight: 500;
  color: var(--ink-3);
}

/* ---- 备件 ---- */
.part {
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--line-soft);
}

.part:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.part-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.part-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--ink-1);
}

.part-code {
  font-size: 11px;
  color: var(--ink-3);
}

.part-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: 4px;
}

.part-usage {
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- AI 输出 ---------- */
.output-panel {
  margin-top: var(--sp-3);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

.output-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.output-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-1);
}

.output-hint {
  font-size: 11px;
  color: var(--ink-3);
}

.output-text {
  padding: var(--sp-4);
  max-height: 460px;
  overflow-y: auto;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-1);
  white-space: pre-wrap;
  word-break: break-word;
}

.muted {
  color: var(--ink-3);
}

/* 窄屏：三栏改成一栏。它们是"参考资料"不是并排对比，
   堆起来读反而更顺 */
@media (max-width: 1100px) {
  .results {
    grid-template-columns: minmax(0, 1fr);
  }

  .hit-text {
    max-height: none;
  }
}
</style>
