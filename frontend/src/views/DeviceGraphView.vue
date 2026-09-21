<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import { getDevicePage, type Device } from '../api/device'
import {
  NODE_SYMBOL,
  getDeviceGraph,
  type DeviceGraph,
  type GraphNode,
  type GraphNodeType,
} from '../api/graph'
import DataPanel from '../components/DataPanel.vue'
import EChart from '../components/EChart.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import { chartPalette } from '../utils/chartColors'
import { useAppStore } from '../stores/app'

const router = useRouter()
const appStore = useAppStore()

const pickedDeviceId = ref<number | undefined>()
const deviceOptions = ref<Device[]>([])
const deviceLoading = ref(false)

const loading = ref(false)
const graph = ref<DeviceGraph | null>(null)
/** 被点中的节点。详情条和动作按钮都由它驱动 */
const selected = ref<GraphNode | null>(null)

const palette = computed(() => chartPalette(appStore.isDark))

// ============================================================
// 数据
// ============================================================

/** 设备下拉的远程搜索（设备多的时候不能一次拉全量） */
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

async function loadGraph(id: number) {
  loading.value = true
  selected.value = null
  try {
    graph.value = await getDeviceGraph(id)
  } catch {
    // 错误提示由 request.ts 的拦截器统一处理
    graph.value = null
  } finally {
    loading.value = false
  }
}

async function onDeviceChange(id: number | undefined) {
  if (id === undefined) {
    graph.value = null
    selected.value = null
    return
  }
  await loadGraph(id)
}

/**
 * 进来直接看第一台设备的关系图。
 *
 * <p>不这么做的话，页面初始是一块空白 + 一个空下拉，用户得先知道
 * "要选一台设备"才发现能用。先画一台出来，交互是自明的。
 */
onMounted(async () => {
  await searchDevices('')
  // Device.id 在类型上是可选的（前端其它地方也按可选用），
  // 所以先取出来判一次，别连着写 first.id —— 那样 TS 收不窄
  const firstId = deviceOptions.value[0]?.id
  if (firstId !== undefined) {
    pickedDeviceId.value = firstId
    await loadGraph(firstId)
  }
})

// ============================================================
// 图
// ============================================================

/** tone → 当前主题下的色值。后端只给语义，色值在这里落地 */
function colorOf(tone: string): string {
  const p = palette.value as unknown as Record<string, string>
  return p[tone] ?? palette.value.idle
}

const option = computed<EChartsOption>(() => {
  const g = graph.value
  const p = palette.value
  if (!g || g.nodes.length === 0) {
    return {}
  }

  const data = g.nodes.map((n) => ({
    // ⚠️ id 放全局唯一键、name 放显示名。ECharts 建节点索引时优先用 id
    //（源码：retrieve(nodes[i].id, nodes[i].name, i)），而边是按同一个键去找节点的 ——
    // 所以 links 里的 source/target 必须引用 id。用 name 的话，
    // 「温度传感器 A」这种重名节点会被合并成一个，边就接错了
    id: n.id,
    name: n.name,
    symbol: NODE_SYMBOL[n.nodeType] ?? 'circle',
    // 中心设备明显大一圈：一屏里要有主次
    symbolSize: n.center ? 58 : 34,
    itemStyle: {
      // 填充用面板底色、只留描边：节点多的时候实心块会糊成一片
      color: p.separator,
      borderColor: colorOf(n.tone),
      borderWidth: n.center ? 3 : 2,
    },
    // 用展开而不是三元给整个 label 赋值：三元的两个分支会推成
    // 「有 fontSize/fontWeight」和「两者都 optional」的联合类型，
    // 而 ECharts 的 fontWeight 只接受 'normal' | 'bold' | ... 这几个字面量，
    // 推宽的 string 对它不可赋值。as const 把 'bold' 钉成字面量
    ...(n.center ? { label: { fontSize: 12, fontWeight: 'bold' as const } } : {}),
  }))

  const links = g.edges.map((e) => ({
    source: e.source,
    target: e.target,
  }))

  return {
    // 悬停只显示名字。类型、状态、关系都在下面的详情条里看 ——
    // 相比跟着鼠标飘的提示框，固定的详情条更好读，也不会被节点压住
    tooltip: { trigger: 'item', formatter: '{b}' },
    series: [
      {
        type: 'graph',
        layout: 'force',
        // 允许缩放和拖拽：节点挤在一起时用户能自己拉开看
        roam: true,
        draggable: true,
        force: {
          repulsion: 340,
          edgeLength: [70, 140],
          gravity: 0.06,
          friction: 0.12,
        },
        // 边不加箭头：这里的关系没有方向语义（"同部门"是谁指向谁？）
        edgeSymbol: ['none', 'none'],
        lineStyle: { color: p.edge, width: 1.2, curveness: 0.06 },
        // 边上的关系名**默认不显示**：十几条边全标上字会糊成一团。
        // 选中某个节点时，它和中心的关系会显示在下面的详情条里
        edgeLabel: { show: false },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 2.4 },
        },
        label: {
          show: true,
          position: 'bottom',
          distance: 5,
          fontSize: 11,
          color: p.ink,
        },
        data,
        links,
      },
    ],
  }
})

/** 点击图形 → 找到对应的节点，显示详情。**不直接跳转** —— 跳转放在详情条的按钮上，
    避免"随手点一下节点就被带走了" */
function onItemClick(id: string | undefined) {
  if (!id || !graph.value) return
  selected.value = graph.value.nodes.find((n) => n.id === id) ?? null
}

// ============================================================
// 详情条
// ============================================================

const centerNodeId = computed(() => (graph.value ? `device:${graph.value.deviceId}` : ''))

/** 选中节点和中心之间的关系名 */
const selectedRelation = computed(() => {
  const node = selected.value
  const g = graph.value
  if (!node || !g) return ''
  const edge = g.edges.find(
    (e) =>
      (e.source === centerNodeId.value && e.target === node.id) ||
      (e.target === centerNodeId.value && e.source === node.id),
  )
  return edge?.label ?? ''
})

/**
 * 选中节点能做什么。
 *
 * <p>设备的动作是「以它为中心」—— 这是这张图真正的导航方式：
 * 沿着同部门的关系走到旁边的设备，再以它为中心展开。
 * 工单和配件只能打开对应的列表页（那两个页面还不支持按 id 深链）。
 */
const selectedAction = computed(() => {
  const node = selected.value
  if (!node || !node.routeId) return null
  if (node.routeType === 'device') {
    // 已经是中心了就没有"以它为中心"这回事
    if (node.id === centerNodeId.value) return null
    return { label: '以它为中心', kind: 'recenter' as const, id: node.routeId }
  }
  if (node.routeType === 'repair') {
    return { label: '打开维修工单', kind: 'repair' as const, id: node.routeId }
  }
  if (node.routeType === 'part') {
    return { label: '打开配件耗材', kind: 'part' as const, id: node.routeId }
  }
  return null
})

async function runAction() {
  const action = selectedAction.value
  if (!action) return
  if (action.kind === 'recenter') {
    pickedDeviceId.value = action.id
    await loadGraph(action.id)
  } else if (action.kind === 'repair') {
    void router.push('/device-repairs')
  } else {
    void router.push('/spare-parts')
  }
}

// ============================================================
// 图例
// ============================================================

/**
 * 图例只列**这张图上真实出现过的**类型。
 *
 * <p>固定列全六种的话，一台既没有保养计划也没有工单的设备会带着一堆
 * 图上有但看不见的图例，用户会去找那个不存在的形状。
 */
const legendItems = computed(() => {
  const g = graph.value
  if (!g) return []
  const counts = new Map<GraphNodeType, { label: string; count: number }>()
  for (const n of g.nodes) {
    const hit = counts.get(n.nodeType)
    if (hit) {
      hit.count += 1
    } else {
      counts.set(n.nodeType, { label: n.typeLabel, count: 1 })
    }
  }
  return [...counts.entries()].map(([type, v]) => ({ type, ...v }))
})

const headDesc = computed(() => {
  const g = graph.value
  if (!g) return '看一台设备在系统里都和什么有关系'
  return `${g.deviceName} 的直接关系：${g.nodes.length} 个节点、${g.edges.length} 条关系`
})
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader title="设备关系图" :desc="headDesc" />

    <!-- ---------- 选设备 ---------- -->
    <DataPanel flush>
      <div class="picker-bar">
        <el-select
          v-model="pickedDeviceId"
          filterable
          remote
          clearable
          reserve-keyword
          :remote-method="searchDevices"
          :loading="deviceLoading"
          placeholder="搜索设备（名称 / 资产编号 / 序列号）"
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

        <span v-if="graph?.note" class="truncate-note">{{ graph.note }}</span>
      </div>
    </DataPanel>

    <!-- ---------- 图 ---------- -->
    <DataPanel class="graph-panel">
      <EChart
        v-if="graph && graph.nodes.length > 0"
        :option="option"
        height="460px"
        @item-click="onItemClick"
      />
      <EmptyState
        v-else-if="pickedDeviceId"
        title="这台设备没有任何关联数据"
        desc="没有部门、分类、工单、保养计划，也没有领用过配件"
      />
      <EmptyState
        v-else
        title="还没有选择设备"
        desc="在上面选一台设备，看它在系统里的关系"
      />

      <!-- 选中某个节点后的详情 + 动作。
           不做成跟着鼠标飘的提示框：那张图上节点挨得近，飘框会被压住 -->
      <div v-if="selected" class="detail-bar">
        <span class="detail-symbol" :class="`sym-${selected.nodeType}`" />
        <span class="detail-type">{{ selected.typeLabel }}</span>
        <span class="detail-name">{{ selected.name }}</span>
        <span v-if="selected.subtitle" class="detail-sub">{{ selected.subtitle }}</span>
        <span v-if="selectedRelation" class="detail-rel">关系：{{ selectedRelation }}</span>
        <el-button v-if="selectedAction" size="small" class="detail-action" @click="runAction">
          {{ selectedAction.label }}
        </el-button>
      </div>
    </DataPanel>

    <!-- ---------- 图例 ----------
         形状管类别、颜色管语义，这两句必须写出来 ——
         不说的话，用户会把"红色"理解成"这台坏了"，而它在配件上其实是"库存告急" -->
    <DataPanel title="怎么看这张图" flush>
      <div class="legend">
        <div class="legend-row">
          <span v-for="it in legendItems" :key="it.type" class="legend-item">
            <span class="legend-shape" :class="`sym-${it.type}`" />
            <span class="legend-label">{{ it.label }}</span>
            <span class="legend-count num">×{{ it.count }}</span>
          </span>
        </div>
        <p class="legend-hint">
          形状表示节点是什么；颜色表示状态 ——
          工单未完结是琥珀色、已完成是绿色、已关闭是灰色，配件库存告急是红色。
          点任意节点看详情，点另一台设备可以「以它为中心」继续看下去。
        </p>
      </div>
    </DataPanel>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* ---------- 选设备 ---------- */
.picker-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
}

.device-picker {
  width: 320px;
  flex: none;
}

.opt-meta {
  margin-left: var(--sp-2);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-3);
}

/* 被截断的说明。用二级墨色而不是更浅的：它是"你没看到的东西"，
   属于必须读到的信息，不是可有可无的附注 */
.truncate-note {
  font-size: 12px;
  color: var(--ink-2);
  min-width: 0;
}

/* ---------- 图 ---------- */
.graph-panel {
  margin-top: var(--sp-3);
}

.detail-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--sunken);
  border-radius: var(--r-control);
}

.detail-type {
  font-size: 12px;
  color: var(--ink-2);
}

.detail-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

/* 副标题和关系名都用二级墨色：它们压在 --sunken 上，
   --ink-3 在那个底色上只有 4.2 左右，不达标（见 tokens.css 的注释） */
.detail-sub,
.detail-rel {
  font-size: 12px;
  color: var(--ink-2);
}

.detail-action {
  margin-left: auto;
}

/* ---------- 图例 ---------- */
.legend {
  padding: var(--sp-3) var(--sp-4) var(--sp-4);
}

.legend-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-5);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
}

.legend-label {
  font-size: 12px;
  color: var(--ink-1);
}

.legend-count {
  font-size: 11px;
  color: var(--ink-3);
}

.legend-hint {
  margin-top: var(--sp-3);
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* ---------- 形状 ----------
   和 ECharts 的 symbol 一一对应，但用 CSS 画：
   ECharts 的 symbol 没有现成的 HTML 版本，为了图例截一张 canvas 太重。
   用 clip-path 而不是内联 SVG，是为了不引入 v-html（那需要额外解释一段静态 HTML） */
.legend-shape,
.detail-symbol {
  flex: none;
  width: 14px;
  height: 14px;
  background: var(--ink-3);
}

.legend-shape {
  width: 12px;
  height: 12px;
}

.sym-device {
  background: var(--signal);
  border-radius: 50%;
}

.sym-dept {
  background: var(--signal-weak);
  border: 1.5px solid var(--signal);
  clip-path: polygon(50% 2%, 98% 96%, 2% 96%);
}

.sym-category {
  background: var(--idle);
}

.sym-repair {
  background: var(--warn);
  border-radius: 3px;
}

.sym-maintenance {
  background: var(--ok);
  clip-path: polygon(50% 100%, 24% 58%, 20% 44%, 22% 30%, 30% 19%,
                     40% 13%, 50% 11%, 60% 13%, 70% 19%, 78% 30%,
                     80% 44%, 76% 58%);
}

.sym-part {
  background: var(--crit);
  clip-path: polygon(50% 0, 100% 50%, 50% 100%, 0 50%);
}
</style>
