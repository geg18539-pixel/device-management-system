<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption, EChartsType } from 'echarts'
import { DEVICE_STATUS, type ChartItem } from '../api/device'

const props = defineProps<{
  statusItems: ChartItem[]
  categoryItems: ChartItem[]
}>()

const statusRef = ref<HTMLDivElement>()
const categoryRef = ref<HTMLDivElement>()

// 用普通变量而不是 ref 存实例：这两个对象不需要触发视图更新，
// 放进 ref 反而会让 Vue 去深度代理整个 ECharts 实例，既浪费又可能出怪问题
let statusChart: EChartsType | null = null
let categoryChart: EChartsType | null = null

/** 状态对应的颜色，和列表页的 el-tag 配色保持一致，看着统一 */
const STATUS_COLORS: Record<string, string> = {
  [DEVICE_STATUS.ONLINE]: '#52c41a',
  [DEVICE_STATUS.OFFLINE]: '#94a3b8',
  [DEVICE_STATUS.REPAIRING]: '#faad14',
  [DEVICE_STATUS.IN_USE]: '#1677ff',
}

function renderStatus() {
  if (!statusChart) return

  const option: EChartsOption = {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 台（{d}%）' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        // 环形图比实心饼图清爽，中间还能放总量
        radius: ['45%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { formatter: '{b} {c}' },
        data: props.statusItems.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: STATUS_COLORS[item.name] ?? '#c084fc' },
        })),
      },
    ],
  }

  // 第二个参数 notMerge=true：数据整体替换时不要和旧配置合并，
  // 否则分类变少时旧的数据项会残留下来
  statusChart.setOption(option, true)
}

function renderCategory() {
  if (!categoryChart) return

  const option: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 44, right: 20, top: 24, bottom: 34 },
    xAxis: {
      type: 'category',
      data: props.categoryItems.map((item) => item.name),
      // interval: 0 强制显示所有类目名，否则 ECharts 会自动隐藏一部分
      axisLabel: { interval: 0, rotate: props.categoryItems.length > 5 ? 30 : 0 },
      axisTick: { alignWithLabel: true },
    },
    yAxis: {
      type: 'value',
      // minInterval: 1 避免出现 "0.5 台" 这种小数刻度
      minInterval: 1,
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 46,
        itemStyle: { color: '#1677ff', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top' },
        data: props.categoryItems.map((item) => item.value),
      },
    ],
  }

  categoryChart.setOption(option, true)
}

function handleResize() {
  statusChart?.resize()
  categoryChart?.resize()
}

onMounted(() => {
  // init 要求容器已经有确定的宽高，所以必须在 onMounted 之后调，
  // 而且容器 CSS 上要给出明确高度（见下面 style 里的 260px）
  if (statusRef.value) {
    statusChart = echarts.init(statusRef.value)
  }
  if (categoryRef.value) {
    categoryChart = echarts.init(categoryRef.value)
  }

  renderStatus()
  renderCategory()

  // 侧边栏折叠、窗口缩放都会改变容器宽度，
  // 而 ECharts 是画在 canvas 上的，不会自动跟着 CSS 变 —— 必须手动 resize
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)

  // 不 dispose 的话，组件卸载后 canvas、事件监听和内部定时器都还挂着，
  // 反复进出这个页面会持续泄漏内存
  statusChart?.dispose()
  categoryChart?.dispose()
  statusChart = null
  categoryChart = null
})

// 数据变化时重绘。deep 是必要的：父组件经常会就地修改数组内容
watch(() => props.statusItems, renderStatus, { deep: true })
watch(() => props.categoryItems, renderCategory, { deep: true })
</script>

<template>
  <div class="charts">
    <el-card shadow="never" class="chart-card">
      <template #header>
        <span class="chart-title">设备状态分布</span>
      </template>
      <div ref="statusRef" class="chart-body" />
    </el-card>

    <el-card shadow="never" class="chart-card">
      <template #header>
        <span class="chart-title">设备分类统计</span>
      </template>
      <div ref="categoryRef" class="chart-body" />
    </el-card>
  </div>
</template>

<style scoped>
.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

/* 窄屏下改成上下排列，否则两个图都会被挤得看不清 */
@media (max-width: 900px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-h);
}

/* 这个高度是必须的：ECharts 靠容器尺寸决定画布大小，
   高度为 0 或 auto 时会初始化失败并打出 warning */
.chart-body {
  height: 260px;
}
</style>
