<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import {
  getDashboardDigest,
  getDashboardStats,
  refreshDashboardDigest,
  type DashboardDigest,
  type DashboardStats,
} from '../api/dashboard'
import { DEVICE_LIFECYCLE, healthGradeTone, type DeviceHealth } from '../api/device'
import AssetComposition from '../components/AssetComposition.vue'
import DataPanel from '../components/DataPanel.vue'
import EChart from '../components/EChart.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import type { PlateTone } from '../utils/plateTone'
import { chartPalette } from '../utils/chartColors'
import { useAppStore } from '../stores/app'

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const stats = ref<DashboardStats | null>(null)

async function loadStats() {
  loading.value = true
  try {
    stats.value = await getDashboardStats()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

// ---------------- AI 今日简报 ----------------

const digest = ref<DashboardDigest | null>(null)
const refreshingDigest = ref(false)

/** 单次生成最多轮询这么多次（每次 3 秒，合 5 分钟 —— 和后端的读超时对齐） */
const DIGEST_MAX_POLLS = 100
let digestTimer: number | null = null
let digestPolls = 0

function stopDigestPolling() {
  if (digestTimer !== null) {
    window.clearInterval(digestTimer)
    digestTimer = null
  }
}

/**
 * 生成中时每 3 秒拉一次，出结果就停。
 *
 * <p>和工单详情里等 AI 分析是同一套做法。
 *
 * <p>轮询上限防的不是死循环 —— 后端的 generating 一定会被 finally 清掉 ——
 * 而是"请求一直失败"那种情况：那时 digest 里还是上一次的响应、generating 一直是 true，
 * 不设上限就会一直打下去。
 */
function startDigestPolling() {
  stopDigestPolling()
  if (!digest.value?.generating) return

  digestPolls = 0
  digestTimer = window.setInterval(async () => {
    digestPolls += 1
    const exhausted = digestPolls >= DIGEST_MAX_POLLS
    try {
      digest.value = await getDashboardDigest()
    } catch {
      if (exhausted) stopDigestPolling()
      return
    }
    if (!digest.value?.generating || exhausted) stopDigestPolling()
  }, 3000)
}

async function loadDigest() {
  try {
    digest.value = await getDashboardDigest()
    startDigestPolling()
  } catch {
    // 摘要拿不到不影响看板其余部分 —— 它本来就是可选的一张卡片
  }
}

async function refreshDigest() {
  refreshingDigest.value = true
  try {
    const vo = await refreshDashboardDigest()
    digest.value = vo
    if (vo.generating) {
      ElMessage.success('已开始重新生成')
      startDigestPolling()
    } else {
      // 刚点完刷新，所以 generating=false 不可能是"已经跑完了"，
      // 只可能是这次没被受理：功能关了，或者距上次不足后端的最小间隔
      ElMessage.info(vo.enabled ? '刚刚已经生成过了，请稍后再试' : '首页 AI 摘要已关闭')
    }
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    refreshingDigest.value = false
  }
}

/**
 * 生成时间的显示值。
 *
 * <p>直接对 ISO 字符串切片，**不经过 Date** —— 全站都是这么处理的。
 * 走 new Date() 的话，"2026-09-21T07:30:00" 这种不带时区后缀的字符串
 * 在不同浏览器上的解析规则并不一致，会出现"显示的时间差几小时"
 * 这种只在部分环境下暴露的问题。
 */
const digestTime = computed(() => {
  const t = digest.value?.generatedAt
  return t ? t.replace('T', ' ').slice(5, 16) : ''
})

onMounted(() => {
  void loadStats()
  void loadDigest()
})

// 组件卸载必须停轮询，否则切走之后它还在后台发请求
onUnmounted(stopDigestPolling)

/** 当前主题下的图表配色。切主题时 computed 重算，图表跟着重绘 */
const palette = computed(() => chartPalette(appStore.isDark))

/**
 * 生命周期状态对应的颜色，和列表页的标签配色保持一致。
 * 用 computed 而不是常量，这样主题切换时会重算
 */
const LIFECYCLE_COLORS = computed<Record<string, string>>(() => {
  const p = palette.value
  return {
    [DEVICE_LIFECYCLE.NORMAL]: p.ok,
    [DEVICE_LIFECYCLE.REPAIR]: p.warn,
    [DEVICE_LIFECYCLE.SCRAPPED]: p.crit,
    [DEVICE_LIFECYCLE.DISABLED]: p.idle,
  }
})

const lifecycleItems = computed(() => stats.value?.lifecycleItems ?? [])
const repairStatusItems = computed(() => stats.value?.repairStatusItems ?? [])
const deptDeviceItems = computed(() => stats.value?.deptDeviceItems ?? [])

// ---------------- 主卡：资产总览 ----------------

/**
 * 主卡的四档构成。
 *
 * <p>数据源和饼图是**同一份** stats，不是各查一次 —— 分开查就可能出现
 * "主卡图例说 6 台、饼图说 7 台"这种对不上的情况。
 *
 * <p>分母口径、以及总数比四档之和多出来的那部分怎么显示，都在
 * AssetComposition 里统一处理；这里只负责把接口字段映射成
 * 「名字 + 数量 + 颜色」。
 */
const heroParts = computed(() => {
  const s = stats.value
  const colors = LIFECYCLE_COLORS.value
  const part = (name: string, value: number) => ({ name, value, color: colors[name] })

  return [
    part(DEVICE_LIFECYCLE.NORMAL, s?.lifecycleNormal ?? 0),
    part(DEVICE_LIFECYCLE.REPAIR, s?.lifecycleRepair ?? 0),
    part(DEVICE_LIFECYCLE.SCRAPPED, s?.lifecycleScrapped ?? 0),
    part(DEVICE_LIFECYCLE.DISABLED, s?.lifecycleDisabled ?? 0),
  ]
})

// ---------------- 待办清单 ----------------

interface TodoItem {
  label: string
  hint: string
  value: number
  unit: string
  tone: PlateTone
  go: () => void
}

/**
 * 待办清单。顺序按**紧急程度**固定，不按数量排 ——
 * 按数量排的话顺序会随数据跳动，用户每次都得重新找。
 *
 * <p>「待处理维修工单」放第一个：它是唯一一条"别人已经报上来、
 * 现在正卡着"的事项，其余三条都属于预防性提醒。
 */
const todos = computed<TodoItem[]>(() => {
  const s = stats.value
  return [
    {
      label: '待处理维修工单',
      hint: '不在「已完成 / 已关闭」的工单',
      value: s?.repairPending ?? 0,
      unit: '单',
      tone: 'crit',
      go: goRepairs,
    },
    {
      label: '维保即将到期',
      hint: `提前 ${s?.maintenanceWarnDays ?? 0} 天预警`,
      value: s?.maintenanceDueCount ?? 0,
      unit: '台',
      tone: 'warn',
      go: goMaintenance,
    },
    {
      label: '配件库存告急',
      hint: '库存已到预警阈值，该补货了',
      value: s?.lowStockCount ?? 0,
      unit: '种',
      tone: 'warn',
      go: goSpareParts,
    },
    {
      label: '保修临期设备',
      hint: `保修到期日在 ${s?.warrantyWarnDays ?? 0} 天内或已过保`,
      value: s?.warrantyExpiringCount ?? 0,
      unit: '台',
      tone: 'warn',
      go: goDevices,
    },
  ]
})

// ---------------- 次卡指标带 ----------------

const borrowedCount = computed(() => stats.value?.borrowedCount ?? 0)
const repairTotal = computed(() => stats.value?.repairTotal ?? 0)
const completionRate = computed(() => stats.value?.repairCompletionRate ?? 0)

// ---------------- 图表配置 ----------------

const lifecycleOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 台（{d}%）' },
  legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
  series: [
    {
      type: 'pie',
      radius: ['45%', '68%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: palette.value.separator, borderWidth: 2 },
      label: { formatter: '{b} {c}' },
      data: lifecycleItems.value.map((item) => ({
        name: item.name,
        value: item.value,
        itemStyle: { color: LIFECYCLE_COLORS.value[item.name] ?? palette.value.idle },
      })),
    },
  ],
}))

const repairOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}：{c} 单' },
  grid: { left: 44, right: 20, top: 24, bottom: 34 },
  xAxis: {
    type: 'category',
    data: repairStatusItems.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: repairStatusItems.value.length > 5 ? 30 : 0 },
    axisTick: { alignWithLabel: true },
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      type: 'bar',
      barMaxWidth: 46,
      itemStyle: { color: palette.value.primary, borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top' },
      data: repairStatusItems.value.map((item) => item.value),
    },
  ],
}))

const deptOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}：{c} 台' },
  grid: { left: 44, right: 20, top: 24, bottom: 50 },
  xAxis: {
    type: 'category',
    data: deptDeviceItems.value.map((item) => item.name),
    axisLabel: { interval: 0, rotate: 30 },
    axisTick: { alignWithLabel: true },
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      type: 'bar',
      barMaxWidth: 40,
      itemStyle: { color: palette.value.primarySoft, borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top' },
      data: deptDeviceItems.value.map((item) => item.value),
    },
  ],
}))

// ---------------- 维保到期 ----------------

/**
 * 距离到期还有几天。负数表示已经过期。
 *
 * <p>只按日期比较，不掺时间：两边都取 YYYY-MM-DD 再算，
 * 避免"今天到期"因为时分秒被算成 -1 天。
 */
function daysUntil(date?: string): number | null {
  if (!date) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(`${date}T00:00:00`)
  if (Number.isNaN(target.getTime())) return null
  return Math.round((target.getTime() - today.getTime()) / 86400000)
}

function warrantyText(date?: string): string {
  const days = daysUntil(date)
  if (days === null) return '—'
  if (days < 0) return `已过保 ${-days} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

/** 保修到期的铭牌色调：已过保 / 临期 / 正常 */
function warrantyTone(date?: string): PlateTone {
  const days = daysUntil(date)
  if (days === null) return 'idle'
  if (days < 0) return 'crit'
  return days <= 30 ? 'warn' : 'idle'
}

function goDevices() {
  void router.push('/devices')
}

function goRepairs() {
  void router.push('/device-repairs')
}

function goMaintenance() {
  void router.push('/maintenance')
}

function goSpareParts() {
  void router.push('/spare-parts')
}

/** 点健康预警里的一行 → 去那台设备的档案页 */
function goDeviceHealth(row: DeviceHealth) {
  void router.push(`/devices/${row.deviceId}`)
}

/** 维保到期情况的铭牌色调：逾期 / 临期 / 正常 */
function maintenanceTone(nextDate?: string): PlateTone {
  const days = daysUntil(nextDate)
  if (days === null) return 'idle'
  if (days < 0) return 'crit'
  return days <= 7 ? 'warn' : 'idle'
}

function maintenanceText(nextDate?: string): string {
  const days = daysUntil(nextDate)
  if (days === null) return '—'
  if (days < 0) return `已逾期 ${-days} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

// ---------------- 页头与面板文案 ----------------
// 放在 computed 里而不是模板里拼字符串：模板里写多行的模板字符串
// 会把换行也带进结果，而且可读性差

const headDesc = computed(() => {
  const total = stats.value?.deviceTotal ?? 0
  if (!total) return '还没有设备数据'
  const depts = stats.value?.deptDeviceItems?.length ?? 0
  return `共 ${total} 台在册设备，分布在 ${depts} 个部门`
})

const maintenanceHint = computed(() => {
  const shown = stats.value?.maintenanceDuePlans?.length ?? 0
  const total = stats.value?.maintenanceDueCount ?? 0
  const warn = stats.value?.maintenanceWarnDays ?? 0
  return `最多显示 ${shown} / 共 ${total} 台，提前 ${warn} 天预警`
})

const warrantyHint = computed(() => {
  const shown = stats.value?.warrantyExpiringDevices?.length ?? 0
  const total = stats.value?.warrantyExpiringCount ?? 0
  return `厂商保修期，和上面的「维保」是两回事；最多显示 ${shown} / 共 ${total} 台`
})

/**
 * 健康预警的说明。
 *
 * <p>"共几台"取的是 riskCount 而不是列表长度 —— 列表被后端截断到前几条，
 * 直接拿长度的话，超过几条时会永远显示那个上限。
 *
 * <p>文案里说清"按分数从低到高"：这样用户知道没显示出来的那些
 * **一定不比这里的好**，不会以为漏了更严重的。
 */
const healthHint = computed(() => {
  const total = stats.value?.healthRiskCount ?? 0
  if (!total) {
    return '所有设备健康分都在 90 以上'
  }
  const shown = stats.value?.healthRiskDevices?.length ?? 0
  return `共 ${total} 台低于 90 分，按分数从低到高列出前 ${shown} 台`
})
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader title="首页看板" :desc="headDesc" />

    <!-- ---------- AI 今日简报 ----------
         放在页头之下、所有数字之上：它是"先读这一段，再去看数字"的东西。

         刻意**不用 DataPanel** —— 那是一段散文，不是一张数据面板，
         做成和数据面板一样的外形会让人以为它也是表格那一类信息。
         左侧一道主色细条给它一个可辨认的身份：全站唯一一块由模型生成的内容。 -->
    <section v-if="digest?.enabled" class="digest">
      <header class="digest-head">
        <span class="digest-badge">AI</span>
        <h2 class="digest-title">今日简报</h2>
        <span v-if="digest.generating" class="digest-state">正在生成…</span>

        <div class="digest-actions">
          <span v-if="digest.model" class="digest-model mono">{{ digest.model }}</span>
          <span v-if="digestTime" class="digest-time num">截至 {{ digestTime }}</span>
          <el-button
            size="small"
            :loading="refreshingDigest"
            :disabled="digest.generating"
            @click="refreshDigest"
          >
            重新生成
          </el-button>
        </div>
      </header>

      <p v-if="digest.available" class="digest-text">{{ digest.text }}</p>
      <p v-else class="digest-empty">{{ digest.message }}</p>

      <!-- 有正文时也可能带提示：那表示最近一次生成失败了，上面是上一次的内容。
           必须写出来，否则用户会拿几天前的判断当今天的用 -->
      <p v-if="digest.available && digest.message" class="digest-note">
        {{ digest.message }}
      </p>
    </section>

    <!-- ---------- 主卡 + 待办 ----------
         主卡占两列：整个页面只有一个"最大的数字"，就是设备总数。
         其余数字都比它小一档，这样视线有落点，不用在一排等宽的卡片里找。 -->
    <div class="overview">
      <AssetComposition
        label="在册设备"
        unit="台"
        :total="stats?.deviceTotal ?? 0"
        :parts="heroParts"
        clickable
        @click="goDevices"
      />

      <DataPanel title="待办" hint="按紧急程度排序" flush>
        <ul class="todo-list">
          <li
            v-for="t in todos"
            :key="t.label"
            class="todo-row"
            @click="t.go()"
          >
            <span class="todo-name">
              {{ t.label }}
              <span class="todo-sub">{{ t.hint }}</span>
            </span>

            <span class="todo-fig">
              <!-- 为 0 的项不抢注意力：用最浅的一级墨色，有东西时才是告警色 -->
              <b class="num" :class="t.value > 0 ? t.tone : 'is-zero'">{{ t.value }}</b>
              <span class="todo-unit">{{ t.unit }}</span>
            </span>
          </li>
        </ul>
      </DataPanel>
    </div>

    <!-- ---------- 设备健康预警 ----------
         放在待办之后、图表之前：它是**要动手处理**的信息，
         不该压在图表明细下面。分数是后端实时算的，不是存的字段 -->
    <DataPanel title="设备健康预警" :hint="healthHint" flush>
      <el-table
        :data="stats?.healthRiskDevices ?? []"
        row-class-name="clickable-row"
        @row-click="goDeviceHealth"
      >
        <el-table-column label="设备" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="hz-name">{{ row.deviceName }}</span>
            <span class="hz-meta mono">{{ row.assetCode || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="deptName" label="所属部门" width="120" show-overflow-tooltip />

        <el-table-column label="健康分" width="86" align="right">
          <template #default="{ row }">
            <b class="num hz-score" :class="row.grade === '高风险' ? 'crit' : 'warn'">
              {{ row.score }}
            </b>
          </template>
        </el-table-column>

        <el-table-column label="等级" width="94">
          <template #default="{ row }">
            <StatusPlate :tone="healthGradeTone(row.grade)">{{ row.grade }}</StatusPlate>
          </template>
        </el-table-column>

        <!-- 为什么扣分。没有原因就不列，别写一堆「—」 -->
        <el-table-column label="扣分原因" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reasons?.length">{{ row.reasons.join('、') }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState
            title="没有健康风险设备"
            desc="所有在册设备的健康分都在 90 分以上；分数由机龄、维修次数、维修费用综合算出"
          />
        </template>
      </el-table>
    </DataPanel>

    <!-- ---------- 次卡指标带 ----------
         借出 / 总量 / 完成率：这三个是"看板读数"，不需要点进去做什么，
         所以做成一条横向指标带而不是三张卡片 —— 和上面的待办区分开。 -->
    <DataPanel flush>
      <div class="strip">
        <div class="strip-cell">
          <div class="strip-label">借出中</div>
          <div class="strip-value num">
            {{ borrowedCount }}<span class="unit">台</span>
          </div>
          <div class="strip-hint">当前有借用人的设备</div>
        </div>

        <div class="strip-cell">
          <div class="strip-label">工单总量</div>
          <div class="strip-value num">
            {{ repairTotal }}<span class="unit">单</span>
          </div>
          <div class="strip-hint">累计报修的全部工单</div>
        </div>

        <div class="strip-cell">
          <div class="strip-label">工单完成率</div>
          <div class="strip-value num">
            {{ completionRate }}<span class="unit">%</span>
          </div>
          <el-progress
            :percentage="completionRate"
            :stroke-width="6"
            :show-text="false"
          />
          <div class="strip-hint">
            已完成 {{ stats?.repairFinished ?? 0 }}、已关闭
            {{ stats?.repairClosed ?? 0 }}
          </div>
        </div>
      </div>
    </DataPanel>

    <!-- ---------- 图表 ---------- -->
    <div class="charts">
      <DataPanel title="设备生命周期分布">
        <EChart v-if="lifecycleItems.length" :option="lifecycleOption" height="280px" />
        <EmptyState
          v-else
          title="还没有设备"
          desc="去「设备管理」建档之后，这里会按生命周期状态显示分布"
        />
      </DataPanel>

      <DataPanel title="工单状态分布">
        <EChart v-if="repairStatusItems.length" :option="repairOption" height="280px" />
        <EmptyState v-else title="还没有工单" desc="报修之后这里会按工单状态显示分布" />
      </DataPanel>

      <DataPanel title="各部门设备数量">
        <EChart v-if="deptDeviceItems.length" :option="deptOption" height="280px" />
        <EmptyState v-else title="还没有设备" desc="设备建档并指定部门之后，这里会按部门统计" />
      </DataPanel>
    </div>

    <!-- ---------- 维保到期清单 ---------- -->
    <DataPanel title="待安排保养的设备" :hint="maintenanceHint" flush>
      <el-table :data="stats?.maintenanceDuePlans ?? []">
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="planName" label="计划名称" width="130" />
        <el-table-column label="周期" width="90">
          <template #default="{ row }">{{ row.cycleDays }} 天</template>
        </el-table-column>
        <el-table-column label="上次保养" width="120">
          <template #default="{ row }">{{ row.lastMaintenanceDate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="下次到期" width="120">
          <template #default="{ row }">{{ row.nextMaintenanceDate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="到期情况" width="130">
          <template #default="{ row }">
            <StatusPlate :tone="maintenanceTone(row.nextMaintenanceDate)">
              {{ maintenanceText(row.nextMaintenanceDate) }}
            </StatusPlate>
          </template>
        </el-table-column>
        <el-table-column prop="maintainer" label="负责人" width="110" />

        <template #empty>
          <EmptyState title="没有需要安排保养的设备" desc="所有设备都还在保养周期内" />
        </template>
      </el-table>
    </DataPanel>

    <!-- ---------- 保修到期清单 ---------- -->
    <DataPanel title="保修即将到期 / 已过保设备" :hint="warrantyHint" flush>
      <el-table :data="stats?.warrantyExpiringDevices ?? []">
        <el-table-column prop="assetCode" label="资产编号" width="140" />
        <el-table-column prop="deviceName" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column prop="manufacturer" label="厂商" width="120" />
        <el-table-column prop="location" label="位置" min-width="110" show-overflow-tooltip />
        <el-table-column label="保修到期" width="120">
          <template #default="{ row }">{{ row.warrantyDate ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="剩余" width="130">
          <template #default="{ row }">
            <StatusPlate :tone="warrantyTone(row.warrantyDate)">
              {{ warrantyText(row.warrantyDate) }}
            </StatusPlate>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="没有临近到期的设备" desc="没有设备在保修预警范围内" />
        </template>
      </el-table>
    </DataPanel>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* ---------- AI 今日简报 ---------- */
.digest {
  margin-bottom: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--line);
  /* 左侧那道主色细条。层次全站都靠底色和分隔线表达，不用投影 ——
     这里是"这一块的性质和别人不一样"的标记，不是装饰 */
  border-left: 3px solid var(--signal);
  border-radius: var(--r-panel);
}

.digest-head {
  display: flex;
  align-items: center;
  /* 窄屏时右侧那串"模型名 + 时间 + 按钮"会挤，让它整组换行 */
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}

.digest-badge {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-plate);
  background: var(--signal);
  color: var(--on-signal);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .3px;
}

.digest-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.digest-state {
  font-size: 12px;
  color: var(--ink-3);
}

.digest-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

/* 模型名和生成时间都是"关于这段内容"的次要信息。
   --ink-3 压在 --surface 上是允许的：tokens.css 里写明了它的三个适用底色
   （surface / canvas / hover），这里正是其中之一 */
.digest-model,
.digest-time {
  font-size: 11px;
  color: var(--ink-3);
}

/* 正文：这是全页唯一一段给人读的散文，字距行高都比数据区松一档 */
.digest-text {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ink-1);
}

.digest-empty {
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* 失败提示走告警色。它出现时上面那段正文是"旧的"，
   不显眼的话用户会直接把旧判断当成今天的 */
.digest-note {
  margin-top: var(--sp-3);
  font-size: 12px;
  color: var(--warn);
}

/* ---------- 主卡 + 待办 ---------- */
.overview {
  display: grid;
  /* 主卡占两份、待办占一份。minmax(0, ...) 是必须的：
     默认的 min-width:auto 会让主卡里的长数字把列撑破 */
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-3);
}

/* 主卡（大数字 + 占比条 + 图例）整体在 components/AssetComposition.vue 里，
   设备台账页也用它。这里只留外层栅格 —— 组件自己的样式是 scoped 的，
   从这个文件里看不到，也不该在这里覆盖。 */

/* ---------- 待办清单 ---------- */
.todo-list {
  display: flex;
  flex-direction: column;
}

.todo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 11px var(--sp-4);
  cursor: pointer;
  border-bottom: 1px solid var(--line-soft);
}

.todo-row:last-child {
  border-bottom: none;
}

.todo-row:hover {
  background: var(--hover);
}

.todo-name {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  font-size: 13px;
  color: var(--ink-1);
}

.todo-sub {
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.todo-fig {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.todo-fig b {
  font-size: 20px;
  font-weight: 600;
  line-height: 1;
}

/* 为 0 的项用二级墨色（不是 --ink-4）。
   --ink-4 只按 --surface 校验过（3.11:1），压到行悬停的 --hover 上
   只有 2.95:1，差一点点不达标 —— 算出来的，不是看着差不多。 */
.todo-fig b.is-zero {
  color: var(--ink-3);
}

.todo-fig b.warn {
  color: var(--warn);
}

.todo-fig b.crit {
  color: var(--crit);
}

.todo-unit {
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 次卡指标带 ---------- */
.strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.strip-cell {
  padding: var(--sp-4) var(--sp-5);
}

.strip-cell + .strip-cell {
  border-left: 1px solid var(--line-soft);
}

.strip-label {
  font-size: 12px;
  color: var(--ink-3);
}

.strip-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--ink-1);
}

.strip-value .unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-3);
}

.strip-hint {
  margin-top: 3px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 完成率的进度条夹在数字和说明之间，给它一点呼吸空间 */
.strip-cell .el-progress {
  margin-top: var(--sp-2);
  max-width: 220px;
}

/* ---------- 设备健康预警 ---------- */
.hz-name {
  display: block;
  color: var(--ink-1);
}

/* 资产编号用等宽：0/O 和 1/l 能分清 */
.hz-meta {
  display: block;
  font-size: 11px;
  color: var(--ink-3);
}

/* 分数用等宽数字，右对齐成一列，「87 / 85 / 88」扫起来是一列而不是犬牙 */
.hz-score {
  font-size: 16px;
  font-weight: 600;
}

.hz-score.warn {
  color: var(--warn);
}

.hz-score.crit {
  color: var(--crit);
}

/* 整行可点 → 去设备档案。光标在 :deep 里给，因为 el-table 的行
   渲染在自己的作用域里，scoped 样式直接写选不中 */
:deep(.clickable-row) {
  cursor: pointer;
}

.muted {
  color: var(--ink-3);
}

/* ---------- 图表 ---------- */
.charts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

/* 面板标题和右侧说明现在由 DataPanel 提供，这里不再需要
   .chart-title / .header-hint 两套样式 */

/* 窄屏下换成纵向排列，否则卡片和图表都会被挤得看不清 */
@media (max-width: 1400px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

/* 主卡和待办并排需要足够的横向空间：待办面板里有一行
   标签 + 说明 + 数字 + 单位，再窄说明文字就被裁掉了 */
@media (max-width: 1100px) {
  .overview {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .strip {
    grid-template-columns: minmax(0, 1fr);
  }

  .strip-cell + .strip-cell {
    border-left: none;
    border-top: 1px solid var(--line-soft);
  }
}
</style>
